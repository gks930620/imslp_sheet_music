package com.test.test.sheetmusic.crawl;

import com.test.test.sheetmusic.common.PdfBytes;
import com.test.test.sheetmusic.edition.EditionFileService;
import com.test.test.sheetmusic.edition.dto.EditionFileUploadDTO;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 판본 1개의 IMSLP 파일 받아오기 (02 §5-7). {@code imslpExecutor} 단일 스레드라 수집 작업과 커넥션을 공유한다.
 * <p>트랜잭션이 없다 — 외부 HTTP·파일 저장을 먼저 끝내고 {@link CrawlPersistService} 로 짧은 DB 트랜잭션만 연다(03 §4).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EditionFileFetcher {

    private final ImslpClient imslpClient;
    private final ImslpGate imslpGate;
    private final CrawlPersistService crawlPersistService;
    private final EditionFileService editionFileService;

    @Async("imslpExecutor")
    public void fetch(Long editionId) {
        String imslpFileId = crawlPersistService.findImslpFileId(editionId).orElse(null);
        if (imslpFileId == null) {
            crawlPersistService.markFetchFailed(editionId, "IMSLP 파일 정보가 없어요");
            return;
        }
        crawlPersistService.markFetching(editionId);

        Path directory = null;
        try {
            directory = Files.createTempDirectory("imslp-fetch-");
            EditionFileUploadDTO uploaded = downloadAndStore(imslpFileId, directory);
            crawlPersistService.attachFetchedFile(editionId, uploaded);
        } catch (Exception e) {
            log.warn("판본 파일 받아오기 실패 - editionId: {}, 사유: {}", editionId, e.getMessage());
            crawlPersistService.markFetchFailed(editionId, CrawlTempFiles.message(e));
        } finally {
            CrawlTempFiles.deleteDirectory(directory);
        }
    }

    /**
     * 워커도 쓰는 경로 — 파일 1개를 받아 저장 전략에 올린다. 실패하면 예외.
     *
     * <p>파일 대기 15초는 <b>여기 한 곳</b>에서만 건다(03 §3, 기획 §9-1). 수집 워커의 파일 루프와
     * 개별 받아오기(02 §5-7)가 모두 이 메서드를 파일마다 1회 지나므로, 한 줄로 두 경로 전부
     * 파일 수만큼 정확히 1회씩 걸린다. 호출부에 또 넣으면 파일당 30초가 되어 중복이다.
     *
     * <p><b>그 한 줄의 자리까지 계약이다</b> — {@code resolveFileUrl()}(대기 페이지 GET) <b>다음</b>,
     * {@code downloadResolvedFile()}(파일 GET) <b>앞</b>. 브라우저가 하는 순서(페이지 → 15초 → 파일)와 같기 때문이다.
     * 카운트다운은 대기 페이지를 받은 순간부터 돌기 시작한다 — 그래서 대기를 페이지 <b>앞</b>에 두면
     * 15초를 다 쓴 뒤에 페이지를 받아 0.3초 만에 파일을 치게 되고, 서버에는 정확히
     * "카운트다운을 안 기다린 클라이언트"로 보인다(2026-09-07 정정).
     * 덧붙여 대기 페이지가 무응답이면 {@code resolveFileUrl()} 이 예외로 끝나서, 받을 수 없는 파일 때문에
     * 15초를 허비하지 않고 PAUSED 백오프로 빨리 물러설 수 있다.
     */
    public EditionFileUploadDTO downloadAndStore(String imslpFileId, Path directory) {
        imslpGate.awaitRequestSlot();
        ImslpFileLocation location = imslpClient.resolveFileUrl(imslpFileId);
        imslpGate.awaitFileWait();
        ImslpDownloadedFile downloaded = imslpClient.downloadResolvedFile(location, directory);
        validate(downloaded);
        return editionFileService.storeFetchedPdf(downloaded.getPath(),
                downloaded.getFileName() == null ? "IMSLP" + imslpFileId + ".pdf" : downloaded.getFileName(),
                "crawler");
    }

    /** 매직바이트 {@code %PDF} + Content-Length 일치 (03 §3 파일 검증). */
    private void validate(ImslpDownloadedFile downloaded) {
        Path path = downloaded.getPath();
        try {
            long actual = Files.size(path);
            if (downloaded.getDeclaredSize() > 0 && downloaded.getDeclaredSize() != actual) {
                throw new IllegalStateException("파일 크기가 응답과 다릅니다");
            }
            if (!PdfBytes.isPdf(path)) {
                throw new IllegalStateException("PDF 파일이 아닙니다");
            }
        } catch (IOException e) {
            throw new IllegalStateException("받은 파일을 읽지 못했습니다", e);
        }
    }
}
