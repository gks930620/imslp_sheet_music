package com.test.test.sheetmusic.work;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 곡 별칭 (01_ERD §3-4). 같은 곡 안 중복은 DB 가 막고, 다른 곡과 겹치는 것은 허용한다. */
@Entity
@Table(name = "work_alias",
        uniqueConstraints = @UniqueConstraint(name = "uk_work_alias",
                columnNames = {"work_id", "alias_normalized"}),
        indexes = @Index(name = "idx_work_alias_normalized", columnList = "alias_normalized"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorkAliasEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "work_id", nullable = false)
    private WorkEntity work;

    @Column(name = "alias", length = 200, nullable = false)
    private String alias;

    @Column(name = "alias_normalized", length = 200, nullable = false)
    private String aliasNormalized;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "source", length = 30, nullable = false)
    private AliasSource source;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    WorkAliasEntity(WorkEntity work, String alias, String aliasNormalized, AliasSource source) {
        this.work = work;
        this.alias = alias;
        this.aliasNormalized = aliasNormalized;
        this.source = source;
    }

    @PrePersist
    void prePersist() {
        this.createdAt = Instant.now();
    }
}
