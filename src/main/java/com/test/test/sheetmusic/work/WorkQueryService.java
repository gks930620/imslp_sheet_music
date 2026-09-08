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
        otherEntities.sort(EditionDtoAssembler.BY_IMSLP_DOWNLOADS);

        // 줄이 되는 것은 파일 있는 판본뿐이고(편성 무관), 파일 없는 판본은 숫자 하나로 간다 (02 §3-3, 2026-09-08).
        // 파일 없는 5칸은 "누를 수 없는 줄" 이라 접이식 헤더의 "(N개)" 가 받을 수 있는 판본 수가 아니게 된다.
        int imslpOnlyCount = 0;
        int downloadableOtherCount = 0;
        for (EditionEntity edition : otherEntities) {
            if (!edition.hasFile()) {
                imslpOnlyCount++;
                continue;
            }
            EditionDTO dto = editionDtoAssembler.toDto(edition, files);
            others.add(dto);
            if (dto.isDownloadable()) {
                downloadableOtherCount++;
            }
        }

        // 추천이 없는 곡만 IMSLP 파일 페이지 후보를 내보낸다 (02 §3-3-2) — 추천이 있으면 링크 자리는 추천 카드 하나다.
        EditionEntity imslpCandidate = recommendedId != null
                ? null : EditionDtoAssembler.imslpCandidateOf(otherEntities);

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
                .imslpOnlyCount(imslpOnlyCount)
                .imslpCandidateEdition(imslpCandidate == null
                        ? null : editionDtoAssembler.toDto(imslpCandidate, files))
                .downloadableOtherCount(downloadableOtherCount)
                .sameComposerWorks(workDtoAssembler.toSummaries(sameComposer))
                .build();
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
