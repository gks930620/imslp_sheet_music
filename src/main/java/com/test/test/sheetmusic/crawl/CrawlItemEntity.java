package com.test.test.sheetmusic.crawl;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 수집 항목 (01_ERD §3-9). */
@Entity
@Table(name = "crawl_item",
        uniqueConstraints = @UniqueConstraint(name = "uk_crawl_item", columnNames = {"job_id", "seq"}),
        indexes = @Index(name = "idx_crawl_item_job_status", columnList = "job_id, status"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CrawlItemEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "job_id", nullable = false)
    private Long jobId;

    @Column(name = "seq", nullable = false)
    private int seq;

    @Column(name = "imslp_url", length = 500, nullable = false)
    private String imslpUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_mode", length = 30, nullable = false)
    private CrawlItemMode itemMode;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 30, nullable = false)
    private CrawlItemStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "fail_reason", length = 30)
    private CrawlFailReason failReason;

    @Column(name = "message", length = 500)
    private String message;

    @Column(name = "work_id")
    private Long workId;

    @Column(name = "edition_count")
    private Integer editionCount;

    @Column(name = "file_count")
    private Integer fileCount;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Builder
    private CrawlItemEntity(Long jobId, int seq, String imslpUrl, CrawlItemMode itemMode, Long workId) {
        this.jobId = jobId;
        this.seq = seq;
        this.imslpUrl = imslpUrl;
        this.itemMode = itemMode;
        this.workId = workId;
        this.status = CrawlItemStatus.PENDING;
    }

    // ===== 도메인 =====

    public void markProcessing() {
        this.status = CrawlItemStatus.PROCESSING;
        this.startedAt = Instant.now();
    }

    public void markSuccess(Long workId, int editionCount, int fileCount, String message) {
        this.status = CrawlItemStatus.SUCCESS;
        this.workId = workId;
        this.editionCount = editionCount;
        this.fileCount = fileCount;
        this.message = message;
        this.failReason = null;
        this.finishedAt = Instant.now();
    }

    public void markHidden(Long workId, int editionCount) {
        this.status = CrawlItemStatus.HIDDEN;
        this.workId = workId;
        this.editionCount = editionCount;
        this.fileCount = 0;
        this.message = "피아노 독주곡이 아닌 것 같아요";
        this.finishedAt = Instant.now();
    }

    public void markSkipped(Long workId) {
        this.status = CrawlItemStatus.SKIPPED;
        this.workId = workId;
        this.message = "이미 있음 — 건너뜀";
        this.finishedAt = Instant.now();
    }

    public void markFailed(CrawlFailReason reason, Long workId, Integer editionCount, Integer fileCount) {
        this.status = CrawlItemStatus.FAILED;
        this.failReason = reason;
        this.message = reason.getMessage();
        this.workId = workId;
        this.editionCount = editionCount;
        this.fileCount = fileCount;
        this.finishedAt = Instant.now();
    }

    public void resetToPending() {
        this.status = CrawlItemStatus.PENDING;
        this.failReason = null;
        this.message = null;
        this.startedAt = null;
        this.finishedAt = null;
    }
}
