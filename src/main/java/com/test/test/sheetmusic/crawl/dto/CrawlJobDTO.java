package com.test.test.sheetmusic.crawl.dto;

import com.test.test.sheetmusic.crawl.CrawlJobStatus;
import com.test.test.sheetmusic.crawl.CrawlStage;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/** 수집 작업 (02 §6-3). */
@Getter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class CrawlJobDTO {

    private Long id;
    private CrawlJobStatus status;
    private boolean stopRequested;
    private boolean stoppedByRestart;
    private boolean fetchFiles;
    private int totalCount;
    private int processedCount;
    private int successCount;
    private int failCount;
    private int skipCount;
    private int hiddenCount;
    private int pendingCount;
    private CurrentItem currentItem;
    private CrawlStage currentStage;
    private Integer currentFileIndex;
    private Integer currentFileTotal;
    private Instant pausedUntil;
    private String failureReason;
    private Long retryOfJobId;
    private String createdBy;
    private Instant createdAt;
    private Instant startedAt;
    private Instant finishedAt;
    private Long elapsedSeconds;
    private Long estimatedRemainingSeconds;

    /** 지금 처리 중인 항목 요약. 처리 중이 아니면 null. */
    @Getter
    @lombok.Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CurrentItem {
        private int seq;
        private String url;
        private String title;
    }
}
