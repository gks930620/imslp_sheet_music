package com.test.test.sheetmusic.composer;

import com.test.test.common.exception.BusinessRuleException;
import com.test.test.common.exception.EntityNotFoundException;
import com.test.test.sheetmusic.composer.dto.ComposerCardDTO;
import com.test.test.sheetmusic.composer.dto.ComposerDetailDTO;
import com.test.test.sheetmusic.composer.dto.ComposerListDTO;
import com.test.test.sheetmusic.composer.repository.ComposerRepository;
import com.test.test.sheetmusic.work.Section;
import com.test.test.sheetmusic.work.repository.ComposerWorkCount;
import com.test.test.sheetmusic.work.repository.WorkRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 공개 작곡가 API (02 §3-5 ~ §3-7). "공개 곡 1개 이상" 의 뜻은 악기 구분이 붙으면서
 * <b>"그 구분에서 공개 곡 1개 이상"</b> 으로 좁아졌다(02 §0-7) — 작곡가는 구분에 속하지 않으므로 곡 수만 구분 기준이다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ComposerQueryService {

    private static final int FEATURED_MAX_LIMIT = 20;

    private final ComposerRepository composerRepository;
    private final WorkRepository workRepository;

    /** §3-5 — 그 구분에 공개 곡 1개 이상, name_ko 가나다순(없으면 뒤로) → name_original 순. */
    public ComposerListDTO list(String section) {
        List<Row> rows = publicRows(Section.from(section));
        rows.sort(Comparator
                .comparing((Row row) -> row.composer.getNameKo() == null)
                .thenComparing(row -> row.composer.getNameKo(), Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(row -> row.composer.getNameOriginal()));

        List<ComposerListDTO.Item> items = new ArrayList<>();
        for (Row row : rows) {
            items.add(ComposerListDTO.Item.from(row.composer, row.workCount));
        }
        return ComposerListDTO.builder().total(items.size()).composers(items).build();
    }

    /** §3-6 — 그 구분의 공개 곡 수 많은 순(동률 name_ko 순). */
    public List<ComposerCardDTO> featured(String section, int limit) {
        if (limit < 1) {
            throw new BusinessRuleException("limit 값이 올바르지 않아요: " + limit);
        }
        List<Row> rows = publicRows(Section.from(section));
        rows.sort(Comparator
                .comparingLong((Row row) -> row.workCount).reversed()
                .thenComparing(row -> row.composer.getNameKo(), Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(row -> row.composer.getId()));

        List<ComposerCardDTO> cards = new ArrayList<>();
        for (Row row : rows.stream().limit(Math.min(limit, FEATURED_MAX_LIMIT)).toList()) {
            cards.add(ComposerCardDTO.from(row.composer, row.workCount));
        }
        return cards;
    }

    /** §3-7 — {@code workCount} 만 구분 기준이고 404 조건은 바뀌지 않는다: 그 구분에 곡이 0개여도 200. */
    public ComposerDetailDTO detail(Long id, String section) {
        Section scope = Section.from(section);
        ComposerEntity composer = composerRepository.findById(id)
                .orElseThrow(() -> EntityNotFoundException.of("작곡가", id));
        return ComposerDetailDTO.from(composer, workRepository.countPublicByComposerIdAndSection(id, scope));
    }

    /** 집계는 작곡가 id + 곡 수만 가져오고(엔티티 통째 로드 금지), 필요한 작곡가만 IN 조회로 붙인다. */
    private List<Row> publicRows(Section section) {
        Map<Long, Long> counts = publicWorkCounts(section);
        if (counts.isEmpty()) {
            return new ArrayList<>();
        }
        List<Row> rows = new ArrayList<>();
        for (ComposerEntity composer : composerRepository.findAllById(counts.keySet())) {
            rows.add(new Row(composer, counts.getOrDefault(composer.getId(), 0L)));
        }
        return rows;
    }

    /** 작곡가 + 공개 곡 수 (내부 집계 결과 — 응답 DTO 가 아니다). */
    private static final class Row {
        private final ComposerEntity composer;
        private final long workCount;

        private Row(ComposerEntity composer, long workCount) {
            this.composer = composer;
            this.workCount = workCount;
        }
    }

    /** 작곡가 id → 그 구분의 공개 곡 수. */
    public Map<Long, Long> publicWorkCounts(Section section) {
        Map<Long, Long> counts = new HashMap<>();
        for (ComposerWorkCount row : workRepository.countPublicWorksGroupedByComposer(section)) {
            counts.put(row.getComposerId(), row.getWorkCount());
        }
        return counts;
    }
}
