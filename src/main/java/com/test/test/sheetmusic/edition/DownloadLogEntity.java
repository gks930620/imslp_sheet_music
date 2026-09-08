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

    /**
     * 다운로드된 판본. <b>판본이 지워지면 NULL</b> 이 된다 (01_ERD §3-7, 02 §5-5) — 로그 자체는 곡의 사실이라
     * 남기고, 사라진 판본을 계속 가리키는 매달린 참조만 끊는다.
     */
    @Column(name = "edition_id")
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
