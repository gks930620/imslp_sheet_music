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

    /** 워커도 쓰는 경로 — 파일 1개를 받아 저장 전략에 올린다. 실패하면 예외. */
    public EditionFileUploadDTO downloadAndStore(String imslpFileId, Path directory) {
        imslpGate.awaitRequestSlot();
        ImslpDownloadedFile downloaded = imslpClient.downloadFile(imslpFileId, directory);
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
