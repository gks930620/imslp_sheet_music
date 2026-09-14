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
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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

    /**
     * 악기 구분 (01_ERD §3-3, 02 §0-7 — 2026-09-10 추가). 곡은 정확히 하나의 구분에 속한다.
     *
     * <p>1차에 이 값을 바꾸는 경로는 없다(관리 API·수집 모두 — 01_ERD §9-2). 수집이 {@code NOT_PIANO_SOLO} 로 숨긴 곡도
     * {@code PIANO} 그대로다: 숨김 사유는 부정형("피아노가 아니다")이라 그 안에 바이올린·총보·성악이 섞여 있어
     * 자동으로 다른 구분이 될 수 없다(기획 04 §2-2).
     *
     * <p>{@code @JdbcTypeCode(VARCHAR)} 는 네이티브 {@code enum(...)} DDL 을 막는다(01_ERD §9-1 사고).
     * {@code @ColumnDefault("'PIANO'")} 는 실데이터 위에 NOT NULL 컬럼을 얹을 때 기존 행을 채우는 근거다(§9-2).
     */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "section", length = 30, nullable = false)
    @ColumnDefault("'PIANO'")
    private Section section;

    @Column(name = "title_ko", length = 300)
    private String titleKo;

    @Column(name = "title_ko_normalized", length = 300)
    private String titleKoNormalized;

    @Column(name = "title_original", length = 300, nullable = false)
    private String titleOriginal;

    @Column(name = "title_original_normalized", length = 300, nullable = false)
    private String titleOriginalNormalized;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
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

    /**
     * 수록곡 안내 (01_ERD §3-3, 2026-09-08 추가) — "이 악보에는 왈츠 3곡이 들어 있어요 …" 처럼 사람이 쓴 완성 문장.
     * 값이 있으면 그 곡은 <b>묶음 악보</b>이고, 검색 항목의 scopeNote 가 COLLECTION 을 다는 근거다(02 §2-2-1).
     */
    @Column(name = "collection_guide", length = 500)
    private String collectionGuide;

    @Column(name = "imslp_url", length = 500)
    private String imslpUrl;

    @Column(name = "hidden", nullable = false)
    private boolean hidden;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "hidden_reason", length = 30)
    private HiddenReason hiddenReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recommended_edition_id")
    private EditionEntity recommendedEdition;

    /**
     * 지금 추천 판본이 사람 눈을 통과했는가 (01_ERD §3-3, 02 §5-6-1 — 2026-09-08 추가).
     *
     * <p>자동 추천 지정은 "전체 악보 · 전곡" 만 보므로 관현악 총보가 추천이 될 수 있어(기획 §10-8),
     * 공개 기준(§8-17)이 "추천 판본 미검수 0곡" 을 요구한다. <b>"누가·언제" 는 남기지 않는다</b> —
     * 이 값이 답하는 질문은 하나뿐이고 그 답은 추천이 바뀌는 순간 무효가 되므로 이력이 아니라 상태다.
     */
    @Column(name = "recommended_edition_reviewed", nullable = false)
    @ColumnDefault("false")
    private boolean recommendedEditionReviewed;

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

    /** {@code section} 을 주지 않으면 {@link Section#PIANO} — 관리 저장·수집·시드가 만드는 곡은 전부 피아노다(01_ERD §9-2). */
    @Builder
    private WorkEntity(ComposerEntity composer, Section section, String titleKo, String titleOriginal, Level level,
                       String compositionYear, String musicalKey, String movements, String movementPageGuide,
                       String collectionGuide, String imslpUrl, boolean hidden, HiddenReason hiddenReason) {
        this.composer = composer;
        this.section = section == null ? Section.PIANO : section;
        changeTitles(titleKo, titleOriginal);
        this.level = level;
        this.compositionYear = compositionYear;
        this.musicalKey = musicalKey;
        this.movements = movements;
        this.movementPageGuide = movementPageGuide;
        this.collectionGuide = blankToNull(collectionGuide);
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
                       String collectionGuide, String imslpUrl, boolean hidden) {
        this.composer = composer;
        changeTitles(titleKo, titleOriginal);
        this.level = level;
        this.compositionYear = compositionYear;
        this.musicalKey = musicalKey;
        this.movements = movements;
        this.movementPageGuide = movementPageGuide;
        this.collectionGuide = blankToNull(collectionGuide);
        this.imslpUrl = imslpUrl;
        changeHidden(hidden, null);
    }

    /** 관리자 숨김은 사유 없음(null). 수집 숨김은 NOT_PIANO_SOLO. */
    public void changeHidden(boolean hidden, HiddenReason reason) {
        this.hidden = hidden;
        this.hiddenReason = hidden ? reason : null;
    }

    /**
     * 재수집이 숨김을 다시 계산한다 — <b>수집이 숨긴 것만 수집이 푼다</b> (02 §6-11 표).
     *
     * <p>판정 규칙이 개정돼도 이미 저장된 {@code hidden} 은 그대로라, 재수집이 한 방향으로만 이것을 정리한다.
     * 관리자가 직접 숨긴 곡({@code hiddenReason == null})과 이미 보이는 곡은 건드리지 않는다 —
     * 수집이 관리자 판단을 뒤집으면 손댈수록 되돌아가는 화면이 된다.
     */
    public void reopenIfCrawlerHid(boolean keyboardSolo) {
        if (keyboardSolo && this.hidden && this.hiddenReason == HiddenReason.NOT_PIANO_SOLO) {
            this.hidden = false;
            this.hiddenReason = null;
        }
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

    /**
     * 시드 1회 백필 (01_ERD §6, 2026-09-08) — <b>비어 있을 때만</b> 채운다.
     * 이미 적재된 곡에 새 컬럼을 채우는 유일한 예외이며, 관리자가 쓴 문구는 덮지 않는다.
     */
    public void fillCollectionGuideIfBlank(String collectionGuide) {
        if (isBlank(this.collectionGuide) && !isBlank(collectionGuide)) {
            this.collectionGuide = collectionGuide.trim();
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

    /**
     * 추천 지정 (02 §5-6). <b>추천이 달라지면 검수는 무효</b>다 — 확인한 것은 "그 판본" 이 아니라 "지금 추천" 이라,
     * 자동 지정(§5-11)이든 관리자 지정이든 새 추천은 아직 아무도 보지 않은 것이다(§5-6-1).
     * 같은 판본을 다시 지정하는 것은 추천이 바뀐 것이 아니므로 확인을 유지한다.
     */
    public void recommend(EditionEntity edition) {
        if (!isSameRecommendation(edition)) {
            this.recommendedEditionReviewed = false;
        }
        this.recommendedEdition = edition;
    }

    /** 추천 해제 — 검수 대상 자체가 없어지므로 검수 상태도 false 로 돌아간다(02 §4-6 · §5-6-1). */
    public void clearRecommendation() {
        this.recommendedEdition = null;
        this.recommendedEditionReviewed = false;
    }

    /** 추천 판본 확인함/되돌리기 (02 §5-6-1). 추천이 없는 곡은 확인할 대상이 없다 — 호출 전에 서비스가 막는다. */
    public void reviewRecommendation(boolean reviewed) {
        this.recommendedEditionReviewed = reviewed && this.recommendedEdition != null;
    }

    /** 지금 추천이 검수를 기다리는가 — 규칙 원본은 {@link WorkRecommendationReview} 하나다(관리 홈·목록 필터와 공유). */
    public boolean needsRecommendationReview() {
        return WorkRecommendationReview.needsReview(this);
    }

    private boolean isSameRecommendation(EditionEntity edition) {
        if (this.recommendedEdition == null || edition == null) {
            return this.recommendedEdition == edition;
        }
        return this.recommendedEdition.getId() != null
                && this.recommendedEdition.getId().equals(edition.getId());
    }

    public void increaseDownloadCount() {
        this.downloadCount++;
    }

    /**
     * 다운로드 파일명 괄호에 쓸 작품번호 (02 §3-4 ③-a, 기획 01 §12-1).
     *
     * <p>작품번호 행이 <b>2개 이상이면 {@code null}</b> 이다 — 여럿 중 하나만 골라 적으면
     * "그 번호만 든 악보"로 읽혀 사용자가 곡을 잘못 고른다. 개수는 값 하나로 알 수 없으므로
     * 호출자인 이 엔티티가 판정하고 {@code DownloadFileName} 의 시그니처는 그대로 둔다.
     *
     * <p>{@link #primaryCatalogNumber()} 와 나누어 둔 이유: 그쪽은 정렬({@code sort_key})이 계속 쓰므로
     * 의미를 바꾸면 목록 정렬이 함께 흔들린다.
     */
    public String fileNameCatalogNumber() {
        return this.catalogNumbers.size() > 1 ? null : primaryCatalogNumber();
    }

    /** 대표 작품번호(sort_order 0) — 정렬(sort_key)에 쓴다. 파일명은 {@link #fileNameCatalogNumber()}. */
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

    /** 보완 필요 항목 (01_ERD §4) — 규칙 원본은 {@link WorkNeedsWork} 하나다(관리 홈·목록 필터와 공유). */
    public List<WorkMissing> missing() {
        return WorkNeedsWork.missing(this);
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
