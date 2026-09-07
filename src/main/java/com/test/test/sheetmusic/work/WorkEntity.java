package com.test.test.sheetmusic.work;

import com.test.test.sheetmusic.common.CatalogSortKey;
import com.test.test.sheetmusic.common.SearchNormalizer;
import com.test.test.sheetmusic.composer.ComposerEntity;
import com.test.test.sheetmusic.edition.EditionEntity;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 곡 (01_ERD §3-3). */
@Entity
@Table(name = "work",
        uniqueConstraints = @UniqueConstraint(name = "uk_work_imslp_url", columnNames = "imslp_url"),
        indexes = {
                @Index(name = "idx_work_composer", columnList = "composer_id"),
                @Index(name = "idx_work_title_ko_normalized", columnList = "title_ko_normalized"),
                @Index(name = "idx_work_title_original_normalized", columnList = "title_original_normalized"),
                @Index(name = "idx_work_download_count", columnList = "download_count"),
                @Index(name = "idx_work_updated_at", columnList = "updated_at"),
                @Index(name = "idx_work_hidden", columnList = "hidden")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorkEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "composer_id", nullable = false)
    private ComposerEntity composer;

    @Column(name = "title_ko", length = 300)
    private String titleKo;

    @Column(name = "title_ko_normalized", length = 300)
    private String titleKoNormalized;

    @Column(name = "title_original", length = 300, nullable = false)
    private String titleOriginal;

    @Column(name = "title_original_normalized", length = 300, nullable = false)
    private String titleOriginalNormalized;

    @Enumerated(EnumType.STRING)
    @Column(name = "level", length = 30)
    private Level level;

    @Column(name = "composition_year", length = 20)
    private String compositionYear;

    @Column(name = "musical_key", length = 50)
    private String musicalKey;

    @Column(name = "movements", length = 500)
    private String movements;

    @Column(name = "movement_page_guide", length = 500)
    private String movementPageGuide;

    @Column(name = "imslp_url", length = 500)
    private String imslpUrl;

    @Column(name = "hidden", nullable = false)
    private boolean hidden;

    @Enumerated(EnumType.STRING)
    @Column(name = "hidden_reason", length = 30)
    private HiddenReason hiddenReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recommended_edition_id")
    private EditionEntity recommendedEdition;

    @Column(name = "download_count", nullable = false)
    private long downloadCount;

    @OneToMany(mappedBy = "work", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<WorkAliasEntity> aliases = new ArrayList<>();

    @OneToMany(mappedBy = "work", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC, id ASC")
    private List<WorkCatalogNumberEntity> catalogNumbers = new ArrayList<>();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Builder
    private WorkEntity(ComposerEntity composer, String titleKo, String titleOriginal, Level level,
                       String compositionYear, String musicalKey, String movements, String movementPageGuide,
                       String imslpUrl, boolean hidden, HiddenReason hiddenReason) {
        this.composer = composer;
        changeTitles(titleKo, titleOriginal);
        this.level = level;
        this.compositionYear = compositionYear;
        this.musicalKey = musicalKey;
        this.movements = movements;
        this.movementPageGuide = movementPageGuide;
        this.imslpUrl = imslpUrl;
        this.hidden = hidden;
        this.hiddenReason = hiddenReason;
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

    public void changeTitles(String titleKo, String titleOriginal) {
        this.titleKo = blankToNull(titleKo);
        this.titleKoNormalized = this.titleKo == null ? null : SearchNormalizer.normalize(this.titleKo);
        this.titleOriginal = titleOriginal == null ? null : titleOriginal.trim();
        this.titleOriginalNormalized = SearchNormalizer.normalize(this.titleOriginal);
    }

    public void update(ComposerEntity composer, String titleKo, String titleOriginal, Level level,
                       String compositionYear, String musicalKey, String movements, String movementPageGuide,
                       String imslpUrl, boolean hidden) {
        this.composer = composer;
        changeTitles(titleKo, titleOriginal);
        this.level = level;
        this.compositionYear = compositionYear;
        this.musicalKey = musicalKey;
        this.movements = movements;
        this.movementPageGuide = movementPageGuide;
        this.imslpUrl = imslpUrl;
        changeHidden(hidden, null);
    }

    /** 관리자 숨김은 사유 없음(null). 수집 숨김은 NOT_PIANO_SOLO. */
    public void changeHidden(boolean hidden, HiddenReason reason) {
        this.hidden = hidden;
        this.hiddenReason = hidden ? reason : null;
    }

    /** 값이 비어 있을 때만 채운다 — 수집이 관리자 입력을 덮어쓰지 않게 (02 §6-10). */
    public void fillMissingMetadata(String compositionYear, String musicalKey, String movements) {
        if (isBlank(this.compositionYear)) {
            this.compositionYear = compositionYear;
        }
        if (isBlank(this.musicalKey)) {
            this.musicalKey = musicalKey;
        }
        if (isBlank(this.movements)) {
            this.movements = movements;
        }
    }

    public void fillImslpUrlIfBlank(String url) {
        if (isBlank(this.imslpUrl)) {
            this.imslpUrl = url;
        }
    }

    public void fillTitleKoIfBlank(String titleKo) {
        if (isBlank(this.titleKo) && !isBlank(titleKo)) {
            this.titleKo = titleKo.trim();
            this.titleKoNormalized = SearchNormalizer.normalize(this.titleKo);
        }
    }

    /** 별칭 전체 교체. 목록에 남아 있는 별칭은 기존 source 를 유지한다(02 §4-8). */
    public void replaceAliases(List<String> newAliases, AliasSource source) {
        Map<String, String> desired = dedupe(newAliases);
        this.aliases.removeIf(alias -> !desired.containsKey(alias.getAliasNormalized()));
        addAliases(newAliases, source);
    }

    /** 정규화 값이 아직 없는 별칭만 추가한다. */
    public void addAliases(List<String> newAliases, AliasSource source) {
        Map<String, String> desired = dedupe(newAliases);
        for (Map.Entry<String, String> entry : desired.entrySet()) {
            if (hasAlias(entry.getKey())) {
                continue;
            }
            this.aliases.add(new WorkAliasEntity(this, entry.getValue(), entry.getKey(), source));
        }
    }

    private boolean hasAlias(String normalized) {
        return this.aliases.stream().anyMatch(a -> a.getAliasNormalized().equals(normalized));
    }

    /** 작품번호 전체 교체 — 목록 순서가 곧 sort_order(0번이 대표). */
    public void replaceCatalogNumbers(List<String> newValues) {
        Map<String, String> desired = dedupe(newValues);
        this.catalogNumbers.removeIf(c -> !desired.containsKey(c.getCatalogValueNormalized()));
        int order = 0;
        for (Map.Entry<String, String> entry : desired.entrySet()) {
            WorkCatalogNumberEntity existing = findCatalog(entry.getKey());
            if (existing != null) {
                existing.changeSortOrder(order);
            } else {
                this.catalogNumbers.add(new WorkCatalogNumberEntity(this, entry.getValue(), entry.getKey(),
                        CatalogSortKey.of(entry.getValue()), order));
            }
            order++;
        }
    }

    /** 없는 작품번호만 뒤에 덧붙인다(수집). */
    public void addCatalogNumbers(List<String> newValues) {
        Map<String, String> desired = dedupe(newValues);
        int order = this.catalogNumbers.size();
        for (Map.Entry<String, String> entry : desired.entrySet()) {
            if (findCatalog(entry.getKey()) != null) {
                continue;
            }
            this.catalogNumbers.add(new WorkCatalogNumberEntity(this, entry.getValue(), entry.getKey(),
                    CatalogSortKey.of(entry.getValue()), order++));
        }
    }

    private WorkCatalogNumberEntity findCatalog(String normalized) {
        return this.catalogNumbers.stream()
                .filter(c -> c.getCatalogValueNormalized().equals(normalized))
                .findFirst()
                .orElse(null);
    }

    public void recommend(EditionEntity edition) {
        this.recommendedEdition = edition;
    }

    public void clearRecommendation() {
        this.recommendedEdition = null;
    }

    public void increaseDownloadCount() {
        this.downloadCount++;
    }

    /** 대표 작품번호(sort_order 0) — 다운로드 파일명·정렬에 쓴다. */
    public String primaryCatalogNumber() {
        return this.catalogNumbers.stream()
                .min((a, b) -> Integer.compare(a.getSortOrder(), b.getSortOrder()))
                .map(WorkCatalogNumberEntity::getCatalogValue)
                .orElse(null);
    }

    public WorkStatus status() {
        EditionEntity recommended = this.recommendedEdition;
        if (recommended == null || recommended.getPdfFileId() == null) {
            return WorkStatus.PREPARING;
        }
        return switch (recommended.getKoreaCopyright()) {
            case FREE -> WorkStatus.READY;
            case RESTRICTED -> WorkStatus.RESTRICTED;
            case UNKNOWN -> WorkStatus.UNKNOWN;
        };
    }

    /** 보완 필요 항목 (01_ERD §4). */
    public List<WorkMissing> missing() {
        List<WorkMissing> missing = new ArrayList<>();
        if (isBlank(this.titleKo)) {
            missing.add(WorkMissing.TITLE_KO);
        }
        if (this.aliases.isEmpty()) {
            missing.add(WorkMissing.ALIAS);
        }
        if (this.level == null) {
            missing.add(WorkMissing.LEVEL);
        }
        if (this.recommendedEdition == null) {
            missing.add(WorkMissing.RECOMMENDED_EDITION);
        }
        return missing;
    }

    public boolean needsWork() {
        return !missing().isEmpty();
    }

    /** 정규화 값 기준 중복 제거 — 입력 순서를 유지한 (normalized 원문) 맵. */
    private static Map<String, String> dedupe(List<String> values) {
        Map<String, String> result = new LinkedHashMap<>();
        if (values == null) {
            return result;
        }
        for (String raw : values) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String normalized = SearchNormalizer.normalize(raw);
            if (normalized.isEmpty()) {
                continue;
            }
            result.putIfAbsent(normalized, raw.trim());
        }
        return result;
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
