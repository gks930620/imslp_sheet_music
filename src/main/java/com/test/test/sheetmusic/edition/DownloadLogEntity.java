package com.test.test.sheetmusic.edition;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 다운로드 기록 (01_ERD §3-7). 개인정보(IP·UA)는 저장하지 않는다. */
@Entity
@Table(name = "download_log",
        indexes = {
                @Index(name = "idx_download_log_downloaded_at", columnList = "downloaded_at"),
                @Index(name = "idx_download_log_work", columnList = "work_id")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DownloadLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "edition_id", nullable = false)
    private Long editionId;

    @Column(name = "work_id", nullable = false)
    private Long workId;

    @Column(name = "downloaded_at", nullable = false)
    private Instant downloadedAt;

    public DownloadLogEntity(Long editionId, Long workId, Instant downloadedAt) {
        this.editionId = editionId;
        this.workId = workId;
        this.downloadedAt = downloadedAt;
    }
}
