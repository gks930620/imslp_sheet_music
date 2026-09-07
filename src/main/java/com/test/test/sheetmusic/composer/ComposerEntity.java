package com.test.test.sheetmusic.composer;

import com.test.test.sheetmusic.common.SearchNormalizer;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 작곡가 (01_ERD §3-1). 정규화 컬럼은 도메인 메서드 안에서만 갱신한다(§1 "정규화 컬럼").
 */
@Entity
@Table(name = "composer",
        uniqueConstraints = @UniqueConstraint(name = "uk_composer_name_original_normalized",
                columnNames = "name_original_normalized"),
        indexes = {
                @Index(name = "idx_composer_name_ko", columnList = "name_ko"),
                @Index(name = "idx_composer_name_ko_normalized", columnList = "name_ko_normalized")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ComposerEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name_ko", length = 100)
    private String nameKo;

    @Column(name = "name_ko_normalized", length = 100)
    private String nameKoNormalized;

    @Column(name = "name_original", length = 200, nullable = false)
    private String nameOriginal;

    @Column(name = "name_original_normalized", length = 200, nullable = false)
    private String nameOriginalNormalized;

    @Column(name = "birth_year")
    private Integer birthYear;

    @Column(name = "death_year")
    private Integer deathYear;

    @Column(name = "nationality", length = 100)
    private String nationality;

    @Column(name = "imslp_url", length = 500)
    private String imslpUrl;

    @OneToMany(mappedBy = "composer", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ComposerAliasEntity> aliases = new ArrayList<>();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Builder
    private ComposerEntity(String nameKo, String nameOriginal, Integer birthYear, Integer deathYear,
                           String nationality, String imslpUrl) {
        changeNames(nameKo, nameOriginal);
        this.birthYear = birthYear;
        this.deathYear = deathYear;
        this.nationality = nationality;
        this.imslpUrl = imslpUrl;
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

    public void changeNames(String nameKo, String nameOriginal) {
        this.nameKo = blankToNull(nameKo);
        this.nameKoNormalized = this.nameKo == null ? null : SearchNormalizer.normalize(this.nameKo);
        this.nameOriginal = nameOriginal;
        this.nameOriginalNormalized = SearchNormalizer.normalize(nameOriginal);
    }

    public void update(String nameKo, String nameOriginal, Integer birthYear, Integer deathYear,
                       String nationality, String imslpUrl) {
        changeNames(nameKo, nameOriginal);
        this.birthYear = birthYear;
        this.deathYear = deathYear;
        this.nationality = nationality;
        this.imslpUrl = imslpUrl;
    }

    /** 한글 표기가 비어 있을 때만 채운다(수집이 만든 작곡가를 관리자 입력이 이기도록). */
    public void fillNameKoIfBlank(String nameKo) {
        if (this.nameKo == null || this.nameKo.isBlank()) {
            this.nameKo = blankToNull(nameKo);
            this.nameKoNormalized = this.nameKo == null ? null : SearchNormalizer.normalize(this.nameKo);
        }
    }

    public void fillImslpUrlIfBlank(String url) {
        if (this.imslpUrl == null || this.imslpUrl.isBlank()) {
            this.imslpUrl = url;
        }
    }

    /**
     * 별칭 목록을 요청 값으로 전체 교체한다(정규화 중복은 하나만 남긴다).
     * <p>지웠다 다시 넣지 않고 <b>차집합만</b> 처리한다 — 같은 정규화 값을 지우고 곧바로 넣으면
     * 같은 flush 안에서 INSERT 가 DELETE 보다 먼저 나가 유니크 제약을 건드린다.
     */
    public void replaceAliases(List<String> newAliases) {
        Set<String> desired = new LinkedHashSet<>();
        if (newAliases != null) {
            for (String raw : newAliases) {
                if (raw != null && !raw.isBlank()) {
                    String normalized = SearchNormalizer.normalize(raw);
                    if (!normalized.isEmpty()) {
                        desired.add(normalized);
                    }
                }
            }
        }
        this.aliases.removeIf(alias -> !desired.contains(alias.getAliasNormalized()));
        addAliases(newAliases);
    }

    /** 정규화 값이 아직 없는 별칭만 추가한다. */
    public void addAliases(List<String> newAliases) {
        if (newAliases == null) {
            return;
        }
        Set<String> seen = new LinkedHashSet<>();
        for (ComposerAliasEntity alias : this.aliases) {
            seen.add(alias.getAliasNormalized());
        }
        for (String raw : newAliases) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String normalized = SearchNormalizer.normalize(raw);
            if (normalized.isEmpty() || !seen.add(normalized)) {
                continue;
            }
            this.aliases.add(new ComposerAliasEntity(this, raw.trim(), normalized));
        }
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
