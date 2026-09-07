package com.test.test.sheetmusic.crawl;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 수집 작업 (01_ERD §3-8). 진행 상태는 전부 DB 에 둔다(03 §3). */
@Entity
@Table(name = "crawl_job",
        indexes = {
                @Index(name = "idx_crawl_job_status", columnList = "status"),
                @Index(name = "idx_crawl_job_created_at", columnList = "created_at")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CrawlJobEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 30, nullable = false)
    private CrawlJobStatus status;

    @Column(name = "stop_requested", nullable = false)
    private boolean stopRequested;

    @Column(name = "stopped_by_restart", nullable = false)
    private boolean stoppedByRestart;

    @Column(name = "fetch_files", nullable = false)
    private boolean fetchFiles;

    @Column(name = "total_count", nullable = false)
    private int totalCount;

    @Column(name = "success_count", nullable = false)
    private int successCount;

    @Column(name = "fail_count", nullable = false)
    private int failCount;

    @Column(name = "skip_count", nullable = false)
    private int skipCount;

    @Column(name = "hidden_count", nullable = false)
    private int hiddenCount;

    @Column(name = "current_item_id")
    private Long currentItemId;

    @Enumerated(EnumType.STRING)
    @Column(name = "current_stage", length = 30)
    private CrawlStage currentStage;

    @Column(name = "current_file_index")
    private Integer currentFileIndex;

    @Column(name = "current_file_total")
    private Integer currentFileTotal;

    @Column(name = "consecutive_unavailable", nullable = false)
    private int consecutiveUnavailable;

    @Column(name = "paused_until")
    private Instant pausedUntil;

    @Column(name = "pause_count", nullable = false)
    private int pauseCount;

    @Column(name = "failure_reason", length = 300)
    private String failureReason;

    @Column(name = "retry_of_job_id")
    private Long retryOfJobId;

    @Column(name = "created_by", length = 100, nullable = false)
    private String createdBy;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Builder
    private CrawlJobEntity(boolean fetchFiles, int totalCount, String createdBy, Long retryOfJobId) {
        this.status = CrawlJobStatus.RUNNING;
        this.fetchFiles = fetchFiles;
        this.totalCount = totalCount;
        this.createdBy = createdBy;
        this.retryOfJobId = retryOfJobId;
        this.startedAt = Instant.now();
    }

    @PrePersist
    void prePersist() {
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    @PreUpdate
    void preUpdate() {
        this.updatedAt = Instant.now();
    }

    // ===== 도메인 =====

    public boolean isActive() {
        return this.status == CrawlJobStatus.RUNNING || this.status == CrawlJobStatus.PAUSED;
    }

    public void requestStop() {
        this.stopRequested = true;
    }

    public void markStopped(boolean byRestart) {
        this.status = CrawlJobStatus.STOPPED;
        this.stoppedByRestart = byRestart;
        this.stopRequested = false;
        this.finishedAt = Instant.now();
        clearProgress();
    }

    public void markStoppedWithReason(String reason) {
        this.status = CrawlJobStatus.STOPPED;
        this.failureReason = reason;
        this.stopRequested = false;
        this.finishedAt = Instant.now();
        clearProgress();
    }

    public void markCompleted() {
        this.status = CrawlJobStatus.COMPLETED;
        this.finishedAt = Instant.now();
        clearProgress();
    }

    public void markPaused(Instant pausedUntil) {
        this.status = CrawlJobStatus.PAUSED;
        this.pausedUntil = pausedUntil;
        this.pauseCount++;
        this.consecutiveUnavailable = 0;
        clearProgress();
    }

    public void resume() {
        this.status = CrawlJobStatus.RUNNING;
        this.pausedUntil = null;
        this.stopRequested = false;
        this.finishedAt = null;
        this.failureReason = null;
        this.consecutiveUnavailable = 0;
        if (this.startedAt == null) {
            this.startedAt = Instant.now();
        }
    }

    public void startItem(Long itemId) {
        this.currentItemId = itemId;
        this.currentStage = CrawlStage.READING_METADATA;
        this.currentFileIndex = null;
        this.currentFileTotal = null;
    }

    public void changeStage(CrawlStage stage, Integer fileIndex, Integer fileTotal) {
        this.currentStage = stage;
        this.currentFileIndex = fileIndex;
        this.currentFileTotal = fileTotal;
    }

    public void clearProgress() {
        this.currentItemId = null;
        this.currentStage = null;
        this.currentFileIndex = null;
        this.currentFileTotal = null;
    }

    public void countResult(CrawlItemStatus status) {
        switch (status) {
            case SUCCESS -> this.successCount++;
            case FAILED -> this.failCount++;
            case SKIPPED -> this.skipCount++;
            case HIDDEN -> this.hiddenCount++;
            default -> {
            }
        }
    }

    public void recordUnavailable() {
        this.consecutiveUnavailable++;
    }

    public void resetUnavailable() {
        this.consecutiveUnavailable = 0;
    }

    public int processedCount() {
        return this.successCount + this.failCount + this.skipCount + this.hiddenCount;
    }
}
