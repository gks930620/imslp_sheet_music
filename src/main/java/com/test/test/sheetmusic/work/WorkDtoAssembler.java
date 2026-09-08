package com.test.test.sheetmusic.work;

import com.test.test.file.entity.FileEntity;
import com.test.test.sheetmusic.composer.ComposerAliasEntity;
import com.test.test.sheetmusic.composer.repository.ComposerRepository;
import com.test.test.sheetmusic.edition.EditionDtoAssembler;
import com.test.test.sheetmusic.edition.EditionEntity;
import com.test.test.sheetmusic.work.dto.ComposerRefDTO;
import com.test.test.sheetmusic.work.dto.ScopeNoteDTO;
import com.test.test.sheetmusic.work.dto.WorkSummaryDTO;
import com.test.test.sheetmusic.work.repository.WorkAliasRepository;
import com.test.test.sheetmusic.work.repository.WorkCatalogNumberRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 곡 목록 응답 조립 (02 §2-2 WorkSummaryDTO).
 * 별칭·작품번호는 곡 id 목록으로 IN 조회해 N+1 을 피한다(03 §7 3번).
 */
@Component
@RequiredArgsConstructor
public class WorkDtoAssembler {

    private final WorkAliasRepository workAliasRepository;
    private final ComposerRepository composerRepository;
    private final WorkCatalogNumberRepository workCatalogNumberRepository;
    private final EditionDtoAssembler editionDtoAssembler;

    /** 검색이 아닌 목록(인기곡·작곡가의 곡·같은 작곡가 곡)은 matchedAlias 가 항상 null 이다. */
    public List<WorkSummaryDTO> toSummaries(List<WorkEntity> works) {
        return toSummaries(works, List.of(), null);
    }

    public List<WorkSummaryDTO> toSummaries(List<WorkEntity> works, List<String> terms, String wholeTerm) {
        if (works.isEmpty()) {
            return List.of();
        }
        List<Long> ids = works.stream().map(WorkEntity::getId).toList();
        Map<Long, List<WorkAliasEntity>> aliases = groupAliases(workAliasRepository.findByWorkIds(ids));
        Map<Long, List<WorkCatalogNumberEntity>> catalogs =
                groupCatalogs(workCatalogNumberRepository.findByWorkIds(ids));
        Map<Long, List<ComposerAliasEntity>> composerAliases = loadComposerAliases(works, terms);

        List<EditionEntity> recommendedEditions = new ArrayList<>();
        for (WorkEntity work : works) {
            if (work.getRecommendedEdition() != null) {
                recommendedEditions.add(work.getRecommendedEdition());
            }
        }
        Map<Long, FileEntity> files = editionDtoAssembler.loadFiles(recommendedEditions);

        List<WorkSummaryDTO> result = new ArrayList<>();
        for (WorkEntity work : works) {
            EditionEntity recommended = work.getRecommendedEdition();
            FileEntity pdf = recommended == null || recommended.getPdfFileId() == null
                    ? null : files.get(recommended.getPdfFileId());
            List<WorkCatalogNumberEntity> workCatalogs = catalogs.getOrDefault(work.getId(), List.of());
            result.add(WorkSummaryDTO.builder()
                    .id(work.getId())
                    .titleKo(work.getTitleKo())
                    .titleOriginal(work.getTitleOriginal())
                    .composer(ComposerRefDTO.from(work.getComposer()))
                    .catalogNumbers(workCatalogs.stream().map(WorkCatalogNumberEntity::getCatalogValue).toList())
                    .level(work.getLevel())
                    .status(work.status())
                    .pageCount(recommended == null ? null : recommended.getPageCount())
                    .fileSize(pdf == null ? null : pdf.getFileSize())
                    // 곡 카드의 미리보기는 추천 판본의 첫 페이지이고 판정 규칙도 판본과 같다 (02 §2-2 · §2-3).
                    .previewUrl(recommended == null
                            ? null : editionDtoAssembler.publicPreviewUrl(recommended, files))
                    .matchedAlias(matchedAlias(work, aliases.getOrDefault(work.getId(), List.of()),
                            workCatalogs, composerAliases.getOrDefault(work.getComposer().getId(), List.of()),
                            terms, wholeTerm))
                    // 02 §2-2-1 — 검색·인기곡·작곡가의 곡·같은 작곡가 곡이 이 한 자리에서 같은 값을 받는다.
                    .scopeNote(ScopeNoteDTO.from(work))
                    .build());
        }
        return result;
    }

    /**
     * 02 §3-1 6번 — 검색어를 별칭으로 설명해야 할 때만 그 별칭 원문을 보여준다.
     *
     * <p>(1) <b>한 단어</b> 검색어가 어떤 별칭과 정확히 같으면 그 별칭("월광" → "월광"),
     * (2) 아니면 주 필드(제목 2개·작곡가 이름·작곡가 별칭·작품번호)로 설명되지 않는 단어를 포함하는
     * 별칭 중 id 가 가장 작은 것. 모두 설명되면 null("쇼팽 녹턴" → 녹턴이 title_ko 에 있으므로 null).
     */
    private String matchedAlias(WorkEntity work, List<WorkAliasEntity> workAliases,
                                List<WorkCatalogNumberEntity> workCatalogs,
                                List<ComposerAliasEntity> composerAliases,
                                List<String> terms, String wholeTerm) {
        if (terms == null || terms.isEmpty()) {
            return null;
        }
        if (terms.size() == 1 && wholeTerm != null && !wholeTerm.isEmpty()) {
            for (WorkAliasEntity alias : workAliases) {
                if (alias.getAliasNormalized().equals(wholeTerm)) {
                    return alias.getAlias();
                }
            }
        }
        for (String term : terms) {
            if (explainedByPrimaryFields(work, workCatalogs, composerAliases, term)) {
                continue;
            }
            for (WorkAliasEntity alias : workAliases) {
                if (alias.getAliasNormalized().contains(term)) {
                    return alias.getAlias();
                }
            }
        }
        return null;
    }

    private boolean explainedByPrimaryFields(WorkEntity work, List<WorkCatalogNumberEntity> workCatalogs,
                                             List<ComposerAliasEntity> composerAliases, String term) {
        if (contains(work.getTitleKoNormalized(), term) || contains(work.getTitleOriginalNormalized(), term)) {
            return true;
        }
        if (contains(work.getComposer().getNameKoNormalized(), term)
                || contains(work.getComposer().getNameOriginalNormalized(), term)) {
            return true;
        }
        for (ComposerAliasEntity alias : composerAliases) {
            if (contains(alias.getAliasNormalized(), term)) {
                return true;
            }
        }
        for (WorkCatalogNumberEntity catalog : workCatalogs) {
            if (contains(catalog.getCatalogValueNormalized(), term)) {
                return true;
            }
        }
        return false;
    }

    private static boolean contains(String value, String term) {
        return value != null && value.contains(term);
    }

    /**
     * 작곡가 별칭을 작곡가 id 로 한 번에 IN 조회한다 — 곡마다 {@code composer.getAliases()} 를 건드리면
     * 목록 20건에 지연 로딩이 20번 붙는다(03 §7 3번). 검색이 아닐 땐(terms 없음) 쓰이지 않으니 조회도 하지 않는다.
     */
    private Map<Long, List<ComposerAliasEntity>> loadComposerAliases(List<WorkEntity> works, List<String> terms) {
        if (terms == null || terms.isEmpty()) {
            return Map.of();
        }
        Set<Long> composerIds = new LinkedHashSet<>();
        for (WorkEntity work : works) {
            composerIds.add(work.getComposer().getId());
        }
        Map<Long, List<ComposerAliasEntity>> map = new LinkedHashMap<>();
        for (ComposerAliasEntity alias : composerRepository.findAliasesByComposerIds(composerIds)) {
            map.computeIfAbsent(alias.getComposer().getId(), key -> new ArrayList<>()).add(alias);
        }
        return map;
    }

    private Map<Long, List<WorkAliasEntity>> groupAliases(List<WorkAliasEntity> aliases) {
        Map<Long, List<WorkAliasEntity>> map = new LinkedHashMap<>();
        for (WorkAliasEntity alias : aliases) {
            map.computeIfAbsent(alias.getWork().getId(), key -> new ArrayList<>()).add(alias);
        }
        return map;
    }

    private Map<Long, List<WorkCatalogNumberEntity>> groupCatalogs(List<WorkCatalogNumberEntity> catalogs) {
        Map<Long, List<WorkCatalogNumberEntity>> map = new LinkedHashMap<>();
        for (WorkCatalogNumberEntity catalog : catalogs) {
            map.computeIfAbsent(catalog.getWork().getId(), key -> new ArrayList<>()).add(catalog);
        }
        return map;
    }
}
