package com.test.test.sheetmusic.edition;

import com.test.test.common.exception.FileUnavailableException;
import com.test.test.file.strategy.FileStorageStrategy;
import com.test.test.sheetmusic.edition.repository.DownloadLogRepository;
import com.test.test.sheetmusic.edition.repository.EditionRepository;
import com.test.test.sheetmusic.member.MyLibraryRecorder;
import com.test.test.sheetmusic.work.WorkEntity;
import java.io.IOException;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * PDF 다운로드 (02 §3-4).
 *
 * <p>순서가 계약이다: <b>메타 읽기(짧은 읽기 트랜잭션) → 바이트 확보(트랜잭션 밖) → 카운터 증가(짧은 쓰기 트랜잭션) → 스트리밍</b>.
 * 바이트를 못 읽으면 503 이고 카운터는 올리지 않는다(03 §4).
 *
 * <p>클래스에 {@code @Transactional} 을 걸지 않는다 — 저장소 바이트 로드(외부 I/O)가
 * 트랜잭션 안에서 돌면 100MB 를 받는 동안 DB 커넥션을 잡고 있게 된다(컨벤션 §1).
 * DB 만 만지는 부분은 {@link DownloadMetaReader}(읽기)와 {@link #recordDownload(Long, Long)}(쓰기)로 분리돼 있다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DownloadService {

    private final DownloadMetaReader downloadMetaReader;
    private final EditionRepository editionRepository;
    private final FileStorageStrategy fileStorageStrategy;
    private final DownloadLogRepository downloadLogRepository;
    private final MyLibraryRecorder myLibraryRecorder;

    /** 다운로드 준비 결과 — 컨트롤러가 헤더를 만들 때 쓴다. */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DownloadFile {
        private Long editionId;
        private String fileName;
        private long contentLength;
        private Resource resource;
    }

    /** 판본·곡·저작권을 검사하고(트랜잭션) 바이트를 준비한다(트랜잭션 밖). 카운터는 아직 올리지 않는다. */
    public DownloadFile prepare(Long editionId) {
        DownloadMetaReader.DownloadMeta meta = downloadMetaReader.read(editionId);

        // 외부 I/O — 트랜잭션 밖 (컨벤션 §1)
        Resource resource = fileStorageStrategy.loadAsResource(meta.getStoredFileName());
        if (resource == null || !resource.exists() || !resource.isReadable()) {
            throw new FileUnavailableException("파일을 준비하지 못했어요");
        }
        long contentLength;
        try {
            contentLength = resource.contentLength();
        } catch (IOException e) {
            throw new FileUnavailableException("파일을 준비하지 못했어요");
        }

        return DownloadFile.builder()
                .editionId(editionId)
                .fileName(meta.getFileName())
                .contentLength(contentLength)
                .resource(resource)
                .build();
    }

    /**
     * 바이트 확보 뒤에만 부른다 — 판본·곡 카운터 +1, {@code download_log} 1행 (01_ERD §7).
     *
     * <p><b>{@code download_log} 는 한 글자도 바뀌지 않는다</b>(03 §24) — 비로그인 다운로드도 그대로 집계에 든다.
     * 주체가 있으면 <b>같은 트랜잭션에서</b> "받은 악보" 선반을 곡 단위로 upsert 한다(01_ERD §3-12).
     *
     * @param userId 이 요청의 로그인 주체. 비로그인이면 {@code null} — 그 기록은 누구의 것도 아니다(기획 05 §3-1)
     */
    @Transactional
    public void recordDownload(Long editionId, Long userId) {
        editionRepository.findById(editionId).ifPresent(edition -> {
            Instant downloadedAt = Instant.now();
            edition.increaseDownloadCount();
            WorkEntity work = edition.getWork();
            work.increaseDownloadCount();
            downloadLogRepository.save(new DownloadLogEntity(editionId, work.getId(), downloadedAt));
            if (userId != null) {
                myLibraryRecorder.recordDownload(userId, work, edition, downloadedAt);
            }
        });
    }
}
