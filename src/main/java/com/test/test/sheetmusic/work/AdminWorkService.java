package com.test.test.sheetmusic.work;

import com.test.test.common.dto.PageResponse;
import com.test.test.common.exception.BusinessRuleException;
import com.test.test.common.exception.DuplicateResourceException;
import com.test.test.common.exception.EntityNotFoundException;
import com.test.test.common.exception.FieldValidationException;
import com.test.test.file.entity.FileEntity;
import com.test.test.sheetmusic.common.ImslpUrlNormalizer;
import com.test.test.sheetmusic.common.SearchNormalizer;
import com.test.test.sheetmusic.composer.ComposerEntity;
import com.test.test.sheetmusic.composer.repository.ComposerRepository;
import com.test.test.sheetmusic.crawl.repository.CrawlItemRepository;
import com.test.test.sheetmusic.edition.AdminEditionService;
import com.test.test.sheetmusic.edition.EditionDtoAssembler;
import com.test.test.sheetmusic.edition.EditionEntity;
import com.test.test.sheetmusic.edition.dto.AdminEditionDTO;
import com.test.test.sheetmusic.edition.repository.DownloadLogRepository;
import com.test.test.sheetmusic.edition.repository.EditionRepository;
import com.test.test.sheetmusic.edition.repository.WorkEditionCount;
import com.test.test.sheetmusic.member.repository.UserWorkDownloadRepository;
import com.test.test.sheetmusic.member.repository.WorkFavoriteRepository;
import com.test.test.sheetmusic.work.dto.AdminWorkDetailDTO;
import com.test.test.sheetmusic.work.dto.AdminWorkListDTO;
import com.test.test.sheetmusic.work.dto.AdminWorkSummaryDTO;
import com.test.test.sheetmusic.work.dto.AliasOverlapDTO;
import com.test.test.sheetmusic.work.dto.ComposerRefDTO;
import com.test.test.sheetmusic.work.dto.WorkSaveDTO;
import com.test.test.sheetmusic.work.repository.WorkAliasRepository;
import com.test.test.sheetmusic.work.repository.WorkRepository;
import com.test.test.sheetmusic.work.repository.WorkSearchCondition;
import com.test.test.sheetmusic.work.repository.WorkSortOrder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 곡 관리 (02 §4-6 ~ §4-10). */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminWorkService {

    private static final List<String> STATUS_VALUES = List.of("READY", "PREPARING", "RESTRICTED", "UNKNOWN",
            "NEEDS_WORK", "NEEDS_RECOMMENDATION_REVIEW", "HIDDEN");

    private final WorkRepository workRepository;
    private final WorkAliasRepository workAliasRepository;
    private final ComposerRepository composerRepository;
    private final EditionRepository editionRepository;
    private final DownloadLogRepository downloadLogRepository;
    private final WorkFavoriteRepository workFavoriteRepository;
    private final UserWorkDownloadRepository userWorkDownloadRepository;
    private final CrawlItemRepository crawlItemRepository;
    private final AdminEditionService adminEditionService;
    private final EditionDtoAssembler editionDtoAssembler;

    // ===== §4-6 목록 =====

    public AdminWorkListDTO list(String q, String status, Long composerId, String level, Pageable pageable) {
        if (status != null && !status.isBlank() && !STATUS_VALUES.contains(status)) {
            throw new BusinessRuleException("상태 값이 올바르지 않아요: " + status);
        }
        WorkSearchCondition condition = WorkSearchCondition.builder()
                .terms(SearchNormalizer.normalizeWords(q))
                .wholeTerm(SearchNormalizer.normalize(q))
                .composerId(composerId)
                .includeHidden(true)
                .statusFilter(status)
                .levelNone("NONE".equalsIgnoreCase(level))
                .levels("NONE".equalsIgnoreCase(level) ? List.of() : Level.parseCsv(level))
                .sortOrder(WorkSortOrder.UPDATED)
                .build();

        Page<WorkEntity> page = workRepository.search(condition, pageable);
        long unfilteredTotal = condition.hasFilters()
                ? workRepository.count(condition.withoutFilters())
                : page.getTotalElements();

        List<Long> ids = page.getContent().stream().map(WorkEntity::getId).toList();
        Map<Long, Long> editionCounts = countEditions(ids);

        List<AdminWorkSummaryDTO> content = new ArrayList<>();
        for (WorkEntity work : page.getContent()) {
            content.add(AdminWorkSummaryDTO.builder()
                    .id(work.getId())
                    .titleKo(work.getTitleKo())
                    .titleOriginal(work.getTitleOriginal())
                    .composer(ComposerRefDTO.from(work.getComposer()))
                    .catalogNumbers(work.getCatalogNumbers().stream()
                            .map(WorkCatalogNumberEntity::getCatalogValue).toList())
                    .level(work.getLevel())
                    .editionCount(editionCounts.getOrDefault(work.getId(), 0L).intValue())
                    .hasRecommended(work.getRecommendedEdition() != null)
                    .recommendationReviewed(work.isRecommendedEditionReviewed())
                    .status(work.status())
                    .needsWork(work.needsWork())
                    .hidden(work.isHidden())
                    .updatedAt(work.getUpdatedAt())
                    .build());
        }

        return AdminWorkListDTO.builder()
                .unfilteredTotal(unfilteredTotal)
                .works(PageResponse.<AdminWorkSummaryDTO>builder()
                        .content(content)
                        .page(page.getNumber())
                        .size(page.getSize())
                        .totalElements(page.getTotalElements())
                        .totalPages(page.getTotalPages())
                        .first(page.isFirst())
                        .last(page.isLast())
                        .build())
                .build();
    }

    // ===== §4-7 상세 =====

    public AdminWorkDetailDTO detail(Long id) {
        return toDetail(findOrThrow(id));
    }

    // ===== §4-8 등록·수정 =====

    @Transactional
    public AdminWorkDetailDTO create(WorkSaveDTO request) {
        validate(request);
        ComposerEntity composer = composerRepository.findById(request.getComposerId())
                .orElseThrow(() -> EntityNotFoundException.of("작곡가", request.getComposerId()));
        String canonicalUrl = canonicalUrl(request.getImslpUrl());
        requireUniqueUrl(canonicalUrl, null);

        WorkEntity work = WorkEntity.builder()
                .composer(composer)
                .titleKo(request.getTitleKo())
                .titleOriginal(request.getTitleOriginal())
                .level(Level.parse(request.getLevel()))
                .compositionYear(request.getCompositionYear())
                .musicalKey(request.getMusicalKey())
                .movements(request.getMovements())
                .movementPageGuide(request.getMovementPageGuide())
                .collectionGuide(request.getCollectionGuide())
                .imslpUrl(canonicalUrl)
                .hidden(request.isHidden())
                .build();
        work.replaceAliases(request.getAliases(), AliasSource.ADMIN);
        work.replaceCatalogNumbers(request.getCatalogNumbers());
        workRepository.save(work);
        return toDetail(work);
    }

    @Transactional
    public AdminWorkDetailDTO update(Long id, WorkSaveDTO request) {
        validate(request);
        WorkEntity work = findOrThrow(id);
        ComposerEntity composer = composerRepository.findById(request.getComposerId())
                .orElseThrow(() -> EntityNotFoundException.of("작곡가", request.getComposerId()));
        String canonicalUrl = canonicalUrl(request.getImslpUrl());
        requireUniqueUrl(canonicalUrl, id);

        work.update(composer, request.getTitleKo(), request.getTitleOriginal(), Level.parse(request.getLevel()),
                request.getCompositionYear(), request.getMusicalKey(), request.getMovements(),
                request.getMovementPageGuide(), request.getCollectionGuide(), canonicalUrl, request.isHidden());
        work.replaceAliases(request.getAliases(), AliasSource.ADMIN);
        work.replaceCatalogNumbers(request.getCatalogNumbers());
        return toDetail(work);
    }

    // ===== §5-6-1 추천 판본 확인함 =====

    /**
     * 추천 판본 확인함/되돌리기 (02 §5-6-1). 응답은 곡 상세(관리) 그대로라 화면이 다시 조회하지 않아도 된다.
     *
     * <p>추천이 없는 곡에 {@code true} 는 400 이다 — 확인할 대상이 없다. {@code false} 로 되돌리기는
     * 추천 유무와 무관하게 허용한다(잘못 눌렀을 때 빠져나갈 길이 없으면 관리자가 확인 자체를 미룬다).
     */
    @Transactional
    public AdminWorkDetailDTO reviewRecommendation(Long workId, Boolean reviewed) {
        WorkEntity work = findOrThrow(workId);
        boolean value = Boolean.TRUE.equals(reviewed);
        if (value && work.getRecommendedEdition() == null) {
            throw FieldValidationException.of("reviewed", "추천 판본이 없어 확인할 수 없어요", reviewed);
        }
        work.reviewRecommendation(value);
        return toDetail(work);
    }

    // ===== §4-9 삭제 =====

    /**
     * 곡 삭제 (01_ERD §7) — 추천 해제 → 판본 → 다운로드 기록 → <b>즐겨찾기·받은 악보</b> → 수집 항목 → 곡.
     *
     * <p>즐겨찾기와 받은 악보는 숨김에는 남지만 <b>삭제에는 함께 사라진다</b>(2026-09-20) —
     * 곡이 없으면 선반에 남길 것도 없기 때문이다(가리킬 곳 없는 줄은 화면에 그릴 수도 없다).
     */
    @Transactional
    public void delete(Long id) {
        WorkEntity work = findOrThrow(id);
        List<EditionEntity> editions = editionRepository.findByWorkIdOrderByIdAsc(id);
        adminEditionService.removeEditions(work, editions);
        downloadLogRepository.deleteByWorkId(id);
        workFavoriteRepository.deleteByWorkId(id);
        userWorkDownloadRepository.deleteByWorkId(id);
        crawlItemRepository.detachWork(id);
        workRepository.delete(work);
    }

    // ===== §4-10 별칭 겹침 =====

    public AliasOverlapDTO aliasOverlap(String alias, Long excludeWorkId) {
        String normalized = SearchNormalizer.normalize(alias);
        long count = 0L;
        if (!normalized.isEmpty()) {
            count = excludeWorkId == null
                    ? workAliasRepository.countWorksWithAlias(normalized)
                    : workAliasRepository.countOtherWorksWithAlias(normalized, excludeWorkId);
        }
        return AliasOverlapDTO.builder().alias(alias).overlapCount(count).build();
    }

    // ===== 내부 =====

    private WorkEntity findOrThrow(Long id) {
        return workRepository.findById(id).orElseThrow(() -> EntityNotFoundException.of("곡", id));
    }

    /**
     * 목록에 필요한 것은 판본 <b>수</b> 하나뿐이라 집계 쿼리로 받는다 (03 §16-1).
     * 판본 행을 전부 읽으면 20곡 × 70판본 = 1,400행을 숫자 하나 때문에 로드하게 된다.
     */
    private Map<Long, Long> countEditions(List<Long> workIds) {
        Map<Long, Long> counts = new LinkedHashMap<>();
        if (workIds.isEmpty()) {
            return counts;
        }
        for (WorkEditionCount row : editionRepository.countByWorkIds(workIds)) {
            counts.put(row.getWorkId(), row.getEditionCount());
        }
        return counts;
    }

    private AdminWorkDetailDTO toDetail(WorkEntity work) {
        List<EditionEntity> editions = editionRepository.findByWorkIdOrderByIdAsc(work.getId());
        Long recommendedId = work.getRecommendedEdition() == null ? null : work.getRecommendedEdition().getId();
        Long candidateId = EditionDtoAssembler.candidateOf(editions, recommendedId);
        Map<Long, FileEntity> files = editionDtoAssembler.loadFiles(editions);

        List<AdminEditionDTO> editionDtos = new ArrayList<>();
        for (EditionEntity edition : EditionDtoAssembler.sortForAdmin(editions, recommendedId, candidateId)) {
            editionDtos.add(editionDtoAssembler.toAdminDto(edition, files, recommendedId, candidateId));
        }

        return AdminWorkDetailDTO.builder()
                .id(work.getId())
                .titleKo(work.getTitleKo())
                .titleOriginal(work.getTitleOriginal())
                .composer(AdminWorkDetailDTO.Composer.from(work.getComposer()))
                .catalogNumbers(work.getCatalogNumbers().stream()
                        .map(WorkCatalogNumberEntity::getCatalogValue).toList())
                .aliases(work.getAliases().stream().map(WorkAliasEntity::getAlias).toList())
                .level(work.getLevel())
                .compositionYear(work.getCompositionYear())
                .musicalKey(work.getMusicalKey())
                .movements(work.getMovements())
                .movementPageGuide(work.getMovementPageGuide())
                .collectionGuide(work.getCollectionGuide())
                .imslpUrl(work.getImslpUrl())
                .hidden(work.isHidden())
                .hiddenReason(work.getHiddenReason())
                .status(work.status())
                .needsWork(work.needsWork())
                .missing(work.missing())
                .recommendedEditionId(recommendedId)
                .candidateEditionId(candidateId)
                .recommendationReviewed(work.isRecommendedEditionReviewed())
                .downloadCount(work.getDownloadCount())
                .hasDownloadHistory(downloadLogRepository.existsByWorkId(work.getId()))
                .editions(editionDtos)
                .createdAt(work.getCreatedAt())
                .updatedAt(work.getUpdatedAt())
                .build();
    }

    private void validate(WorkSaveDTO request) {
        if (request.getTitleOriginal() == null || request.getTitleOriginal().isBlank()) {
            throw FieldValidationException.of("titleOriginal", "원어 제목을 입력해 주세요", request.getTitleOriginal());
        }
        if (request.getImslpUrl() != null && !request.getImslpUrl().isBlank()
                && ImslpUrlNormalizer.canonicalize(request.getImslpUrl()) == null) {
            throw FieldValidationException.of("imslpUrl", "IMSLP 작품 페이지 주소를 입력해 주세요", request.getImslpUrl());
        }
    }

    private String canonicalUrl(String rawUrl) {
        return (rawUrl == null || rawUrl.isBlank()) ? null : ImslpUrlNormalizer.canonicalize(rawUrl);
    }

    private void requireUniqueUrl(String canonicalUrl, Long selfId) {
        if (canonicalUrl == null) {
            return;
        }
        workRepository.findByImslpUrl(canonicalUrl).ifPresent(existing -> {
            if (!existing.getId().equals(selfId)) {
                throw new DuplicateResourceException("이미 등록된 IMSLP 주소예요");
            }
        });
    }
}
