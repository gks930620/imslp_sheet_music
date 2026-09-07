package com.test.test.sheetmusic.crawl;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 워커가 트랜잭션 밖에서 보는 작업 상태 스냅샷 (지연 로딩 없는 값만). */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CrawlJobState {

    private Long id;
    private CrawlJobStatus status;
    private boolean stopRequested;
    private boolean fetchFiles;
    private Instant pausedUntil;
    private int pauseCount;
    private int consecutiveUnavailable;

    public static CrawlJobState from(CrawlJobEntity job) {
        return CrawlJobState.builder()
                .id(job.getId())
                .status(job.getStatus())
                .stopRequested(job.isStopRequested())
                .fetchFiles(job.isFetchFiles())
                .pausedUntil(job.getPausedUntil())
                .pauseCount(job.getPauseCount())
                .consecutiveUnavailable(job.getConsecutiveUnavailable())
                .build();
    }
}
