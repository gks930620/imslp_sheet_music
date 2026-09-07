package com.test.test.sheetmusic.composer;

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

/** 작곡가 검색용 별칭 (01_ERD §3-2). */
@Entity
@Table(name = "composer_alias",
        uniqueConstraints = @UniqueConstraint(name = "uk_composer_alias",
                columnNames = {"composer_id", "alias_normalized"}),
        indexes = @Index(name = "idx_composer_alias_normalized", columnList = "alias_normalized"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ComposerAliasEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "composer_id", nullable = false)
    private ComposerEntity composer;

    @Column(name = "alias", length = 200, nullable = false)
    private String alias;

    @Column(name = "alias_normalized", length = 200, nullable = false)
    private String aliasNormalized;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    ComposerAliasEntity(ComposerEntity composer, String alias, String aliasNormalized) {
        this.composer = composer;
        this.alias = alias;
        this.aliasNormalized = aliasNormalized;
    }

    @PrePersist
    void prePersist() {
        this.createdAt = Instant.now();
    }
}
