package com.test.test.sheetmusic.work;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 작품번호 (01_ERD §3-5). value 는 H2 예약어라 catalog_value 를 쓴다. */
@Entity
@Table(name = "work_catalog_number",
        uniqueConstraints = @UniqueConstraint(name = "uk_work_catalog",
                columnNames = {"work_id", "catalog_value_normalized"}),
        indexes = @Index(name = "idx_work_catalog_normalized", columnList = "catalog_value_normalized"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorkCatalogNumberEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "work_id", nullable = false)
    private WorkEntity work;

    @Column(name = "catalog_value", length = 100, nullable = false)
    private String catalogValue;

    @Column(name = "catalog_value_normalized", length = 100, nullable = false)
    private String catalogValueNormalized;

    @Column(name = "sort_key", length = 120, nullable = false)
    private String sortKey;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    WorkCatalogNumberEntity(WorkEntity work, String catalogValue, String catalogValueNormalized,
                            String sortKey, int sortOrder) {
        this.work = work;
        this.catalogValue = catalogValue;
        this.catalogValueNormalized = catalogValueNormalized;
        this.sortKey = sortKey;
        this.sortOrder = sortOrder;
    }

    @PrePersist
    void prePersist() {
        this.createdAt = Instant.now();
    }

    void changeSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }
}
