package com.test.test.sheetmusic.work;

import com.test.test.common.dto.PageResponse;
import com.test.test.common.exception.BusinessRuleException;
import com.test.test.common.exception.EntityNotFoundException;
import com.test.test.common.exception.FieldValidationException;
import com.test.test.file.entity.FileEntity;
import com.test.test.sheetmusic.common.SearchNormalizer;
import com.test.test.sheetmusic.composer.ComposerEntity;
import com.test.test.sheetmusic.composer.dto.ComposerCardDTO;
import com.test.test.sheetmusic.composer.repository.ComposerRepository;
import com.test.test.sheetmusic.edition.EditionDtoAssembler;
import com.test.test.sheetmusic.edition.EditionEntity;
import com.test.test.sheetmusic.edition.EditionKind;
import com.test.test.sheetmusic.edition.dto.EditionDTO;
import com.test.test.sheetmusic.edition.repository.EditionRepository;
import com.test.test.sheetmusic.work.dto.ComposerRefDTO;
import com.test.test.sheetmusic.work.dto.ComposerWorksResponseDTO;
import com.test.test.sheetmusic.work.dto.WorkDetailDTO;
import com.test.test.sheetmusic.work.dto.WorkSearchResponseDTO;
import com.test.test.sheetmusic.work.dto.WorkSummaryDTO;
import com.test.test.sheetmusic.work.repository.ComposerWorkCount;
import com.test.test.sheetmusic.work.repository.WorkRepository;
import com.test.test.sheetmusic.work.repository.WorkSearchCondition;
import com.test.test.sheetmusic.work.repository.WorkSortOrder;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 공개 곡 API (02 §3-1 ~ §3-3, §3-8). */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WorkQueryService {

    private static final int MAX_QUERY_LENGTH = 100;
    private static final int COMPOSER_CARD_LIMIT = 3;
    private static final int SAME_COMPOSER_LIMIT = 5;
    private static final int POPULAR_MAX_LIMIT = 20;
    /**
     * 곡 상세의 "다른 판본" 중 <b>파일 없는</b> 판본을 몇 개까지 실어 보내는가 (02 §3-3, 2026-09-07 개정).
     * 수집 결과는 곡당 판본 70개인데 그중 파일이 있는 건 최대 2개다 — 전량을 주면 화면에
     * "파일 없음 · IMSLP에서 보기" 행이 68줄 깔려 "판본 고민 없이 1개" 라는 제품 약속과 어긋난다.
     * 파일 있는 판본은 개수를 우리가 통제하므로 상한을 두지 않는다.
     */
    private static final int FILELESS_OTHER_EDITION_LIMIT = 5;

    private final WorkRepository workRepository;
    private final ComposerRepository composerRepository;
    private final EditionRepository editionRepository;
    private final WorkDtoAssembler workDtoAssembler;
    private final EditionDtoAssembler editionDtoAssembler;

    // ===== §3-1 검색 =====

    public WorkSearchResponseDTO search(String q, String level, String pages, Boolean downloadable,
                                        Pageable pageable) {
        if (q == null || q.isBlank()) {
            throw FieldValidationException.of("q", "검색어를 입력해 주세요", q);
        }
        if (q.length() > MAX_QUERY_LENGTH) {
            throw FieldValidationException.of("q", "검색어는 100자까지 입력할 수 있어요", q);
        }
        List<String> terms = SearchNormalizer.normalizeWords(q);
        if (terms.isEmpty()) {
            throw FieldValidationException.of("q", "검색어를 입력해 주세요", q);
        }

        WorkSearchCondition condition = WorkSearchCondition.builder()
                .terms(terms)
                .wholeTerm(SearchNormalizer.normalize(q))
                .levels(Level.parseCsv(level))
                .pages(PagesFilter.from(pages))
                .downloadableOnly(Boolean.TRUE.equals(downloadable))
                .sortOrder(WorkSortOrder.RELEVANCE)
                .build();

        Page<WorkEntity> page = workRepository.search(condition, pageable);
        long unfilteredTotal = condition.hasFilters()
                ? workRepository.count(condition.withoutFilters())
                : page.getTotalElements();

        // 작곡가별 공개 곡 수는 한 번만 집계해 카드 조립·정렬에 함께 쓴다(요청당 1회).
        Map<Long, Long> counts = publicWorkCounts();
        List<ComposerEntity> matched = matchingComposersWithPublicWorks(terms, counts);
        List<ComposerCardDTO> cards = new ArrayList<>();
        for (ComposerEntity composer : matched.stream().limit(COMPOSER_CARD_LIMIT).toList()) {
            cards.add(ComposerCardDTO.from(composer, counts.getOrDefault(composer.getId(), 0L)));
        }

        return WorkSearchResponseDTO.builder()
                .q(q)
                .composers(cards)
                .composerMatchCount(matched.size())
                .unfilteredTotal(unfilteredTotal)
                .works(toPageResponse(page,
                        workDtoAssembler.toSummaries(page.getContent(), terms, condition.getWholeTerm())))
                .build();
    }

    // ===== §3-2 인기곡 =====

    public List<WorkSummaryDTO> popular(int limit) {
        if (limit < 1) {
            throw new BusinessRuleException("limit 값이 올바르지 않아요: " + limit);
        }
        int capped = Math.min(limit, POPULAR_MAX_LIMIT);
        return workDtoAssembler.toSummaries(workRepository.findPopular(capped));
    }

    // ===== §3-3 곡 상세 =====

    public WorkDetailDTO detail(Long id) {
        WorkEntity work = workRepository.findById(id)
                .filter(w -> !w.isHidden())
                .orElseThrow(() -> EntityNotFoundException.of("곡", id));

        List<EditionEntity> editions = editionRepository.findByWorkIdOrderByIdAsc(id);
        Map<Long, FileEntity> files = editionDtoAssembler.loadFiles(editions);
        Long recommendedId = work.getRecommendedEdition() == null ? null : work.getRecommendedEdition().getId();

        EditionDTO recommended = null;
        List<EditionDTO> others = new ArrayList<>();
        List<EditionEntity> otherEntities = new ArrayList<>();
        for (EditionEntity edition : editions) {
            if (edition.getId().equals(recommendedId)) {
                recommended = editionDtoAssembler.toDto(edition, files);
            } else {
                otherEntities.add(edition);
            }
        }
        otherEntities.sort(Comparator
                .comparingInt((EditionEntity e) -> e.getImslpDownloadCount() == null
                        ? Integer.MIN_VALUE : e.getImslpDownloadCount()).reversed()
                .thenComparing(EditionEntity::getId));

        // 파일 있는 판본은 전부(편성 무관), 파일 없는 판본은 전체 악보만 상위 5개 (02 §3-3, 2026-09-08 개정).
        // 개수(otherEditionsTotal·downloadableOtherCount)는 잘림과 무관하되 목록과 같은 모집단으로 센다.
        List<EditionEntity> fileless = new ArrayList<>();
        int downloadableOtherCount = 0;
        for (EditionEntity edition : otherEntities) {
            if (!edition.hasFile()) {
                if (isInScope(edition)) {
                    fileless.add(edition);
                }
                continue;
            }
            EditionDTO dto = editionDtoAssembler.toDto(edition, files);
            others.add(dto);
            if (dto.isDownloadable()) {
                downloadableOtherCount++;
            }
        }
        int otherEditionsTotal = others.size() + fileless.size();
        for (EditionEntity edition : fileless.stream().limit(FILELESS_OTHER_EDITION_LIMIT).toList()) {
            others.add(editionDtoAssembler.toDto(edition, files));
        }

        List<WorkEntity> sameComposer = workRepository.findSameComposerWorks(
                work.getComposer().getId(), work.getId(), PageRequest.of(0, SAME_COMPOSER_LIMIT));

        return WorkDetailDTO.builder()
                .id(work.getId())
                .titleKo(work.getTitleKo())
                .titleOriginal(work.getTitleOriginal())
                .composer(ComposerRefDTO.from(work.getComposer()))
                .catalogNumbers(work.getCatalogNumbers().stream()
                        .map(WorkCatalogNumberEntity::getCatalogValue).toList())
                .level(work.getLevel())
                .status(work.status())
                .aliases(work.getAliases().stream().map(WorkAliasEntity::getAlias).toList())
                .compositionYear(work.getCompositionYear())
                .musicalKey(work.getMusicalKey())
                .movements(work.getMovements())
                .movementPageGuide(work.getMovementPageGuide())
                .collectionGuide(work.getCollectionGuide())
                .imslpUrl(work.getImslpUrl())
                .composerImslpUrl(work.getComposer().getImslpUrl())
                .recommendedEdition(recommended)
                .otherEditions(others)
                .otherEditionsTotal(otherEditionsTotal)
                .downloadableOtherCount(downloadableOtherCount)
                .sameComposerWorks(workDtoAssembler.toSummaries(sameComposer))
                .build();
    }

    /**
     * 파일 없는 판본을 공개 곡 상세에 안내할 것인가 (02 §3-3, 2026-09-08 개정).
     *
     * <p>그 목록의 유일한 용도는 "IMSLP 에 가면 더 있다" 는 안내다. 실측은 곡당 판본 평균 79.8개(최대 207개)이고
     * 그중 73~83%가 편곡({@code ARRANGEMENT})이라, 거르지 않으면 안내가 기타·성악·2대 피아노 스캔으로 채워진다 —
     * 1차 범위가 <b>피아노 독주</b>(기획 §0-2)인데 안내로서 틀린 정보다. 파트보({@code PARTS})가 있다는 것은
     * 애초에 앙상블 곡이라는 뜻이라 같이 뺀다. <b>파일 있는 판본은 편성과 무관하게 전부 남긴다</b> —
     * 우리가 실제로 줄 수 있는 것이고, 편곡에 파일이 붙었다면 관리자가 의도해 붙인 것이다.
     * 관리 화면(§4-7)은 계속 전부 보여 준다.
     */
    private static boolean isInScope(EditionEntity edition) {
        return edition.getKind() == EditionKind.COMPLETE_SCORE;
    }

    // ===== §3-8 작곡가의 곡 =====

    public ComposerWorksResponseDTO composerWorks(Long composerId, String sort, String level, String pages,
                                                  Boolean downloadable, Pageable pageable) {
        if (!composerRepository.existsById(composerId)) {
            throw EntityNotFoundException.of("작곡가", composerId);
        }
        WorkSearchCondition condition = WorkSearchCondition.builder()
                .terms(List.of())
                .composerId(composerId)
                .levels(Level.parseCsv(level))
                .pages(PagesFilter.from(pages))
                .downloadableOnly(Boolean.TRUE.equals(downloadable))
                .sortOrder(WorkSortOrder.fromComposerWorksSort(sort))
                .build();

        Page<WorkEntity> page = workRepository.search(condition, pageable);
        long unfilteredTotal = condition.hasFilters()
                ? workRepository.count(condition.withoutFilters())
                : page.getTotalElements();

        return ComposerWorksResponseDTO.builder()
                .unfilteredTotal(unfilteredTotal)
                .works(toPageResponse(page, workDtoAssembler.toSummaries(page.getContent())))
                .build();
    }

    // ===== 내부 =====

    /** 공개 곡이 1개 이상인 작곡가 id → 곡 수. */
    public Map<Long, Long> publicWorkCounts() {
        Map<Long, Long> counts = new HashMap<>();
        for (ComposerWorkCount row : workRepository.countPublicWorksGroupedByComposer()) {
            counts.put(row.getComposerId(), row.getWorkCount());
        }
        return counts;
    }

    /** 집계 맵은 호출자가 한 번 계산해 넘긴다 — 검색 1회에 같은 집계를 두 번 돌리지 않기 위해서다. */
    private List<ComposerEntity> matchingComposersWithPublicWorks(List<String> terms, Map<Long, Long> counts) {
        List<Long> ids = composerRepository.findMatchingIds(terms);
        if (ids.isEmpty()) {
            return List.of();
        }
        List<ComposerEntity> composers = new ArrayList<>();
        for (ComposerEntity composer : composerRepository.findAllById(ids)) {
            if (counts.getOrDefault(composer.getId(), 0L) > 0) {
                composers.add(composer);
            }
        }
        composers.sort(Comparator
                .comparingLong((ComposerEntity c) -> counts.getOrDefault(c.getId(), 0L)).reversed()
                .thenComparing(ComposerEntity::getNameKo, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(ComposerEntity::getId));
        return composers;
    }

    private PageResponse<WorkSummaryDTO> toPageResponse(Page<WorkEntity> page, List<WorkSummaryDTO> content) {
        return PageResponse.<WorkSummaryDTO>builder()
                .content(content)
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .first(page.isFirst())
                .last(page.isLast())
                .build();
    }
}
