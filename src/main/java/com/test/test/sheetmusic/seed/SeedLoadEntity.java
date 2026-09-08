package com.test.test.sheetmusic.seed;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 시드 CSV 행의 적재 기록 (01_ERD §3-10, 2026-09-07 추가).
 *
 * <p>로더는 "없으면 INSERT" 하는 삽입 전용인데, 로컬 DB 가 파일 DB 가 되고(03 §17) 운영도 배포마다 로더가 돌면서
 * <b>관리자가 §4-9 로 지운 시드 곡이 다음 기동에 새 id 로 되살아났다</b>. 자연키 적재 기록을 남기면
 * "지웠으니 다시 넣지 않는다" 와 "CSV 에 새로 추가된 행은 다음 기동에 들어온다" 를 동시에 만족한다.
 */
@Entity
@Table(name = "seed_load",
        uniqueConstraints = @UniqueConstraint(name = "uk_seed_load", columnNames = {"seed_type", "natural_key"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SeedLoadEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "seed_type", length = 30, nullable = false)
    private SeedType seedType;

    /** 작곡가 {@code name_original_normalized}, 곡 {@code imslp_url}(정규 형태). */
    @Column(name = "natural_key", length = 500, nullable = false)
    private String naturalKey;

    @Column(name = "loaded_at", nullable = false)
    private Instant loadedAt;

    @Builder
    private SeedLoadEntity(SeedType seedType, String naturalKey) {
        this.seedType = seedType;
        this.naturalKey = naturalKey;
    }

    @PrePersist
    void prePersist() {
        this.loadedAt = Instant.now();
    }
}
