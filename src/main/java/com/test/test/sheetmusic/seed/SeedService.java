package com.test.test.sheetmusic.seed;

import com.test.test.sheetmusic.common.ImslpUrlNormalizer;
import com.test.test.sheetmusic.common.SearchNormalizer;
import com.test.test.sheetmusic.composer.ComposerEntity;
import com.test.test.sheetmusic.composer.repository.ComposerRepository;
import com.test.test.sheetmusic.seed.repository.SeedLoadRepository;
import com.test.test.sheetmusic.work.AliasSource;
import com.test.test.sheetmusic.work.Level;
import com.test.test.sheetmusic.work.WorkEntity;
import com.test.test.sheetmusic.work.repository.WorkRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 시드 적재 (01_ERD §6·§3-10, 03 §8) — <b>멱등·삽입 전용</b>.
 * 작곡가 자연키 {@code name_original_normalized}, 곡 자연키 {@code imslp_url}. 이미 있으면 어떤 컬럼도 덮어쓰지 않는다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class SeedService {

    private static final String COMPOSERS_CSV = "seed/composers.csv";
    private static final String WORKS_CSV = "seed/works.csv";
    private static final String MULTI_SEPARATOR = "|";

    private final SeedCsvReader csvReader;
    private final ComposerRepository composerRepository;
    private final WorkRepository workRepository;
    private final SeedLoadRepository seedLoadRepository;

    @Transactional
    public void load() {
        int composers = loadComposers();
        int works = loadWorks();
        int collectionGuides = backfillCollectionGuides();
        log.info("시드 적재 완료 - 새 작곡가 {}명, 새 곡 {}곡, 수록곡 안내 {}건 채움", composers, works, collectionGuides);
    }

    /**
     * {@code collection_guide} 1회 백필 (01_ERD §6, 2026-09-08) — <b>삽입 전용 원칙의 유일한 예외</b>다.
     *
     * <p>"한 번 적재한 행은 다시 적재하지 않는다" 규칙 때문에, 컬럼을 새로 만들면 이미 적재된 시드 곡 50개는
     * 영원히 NULL 이다(로컬 파일 DB·운영 모두). 그러면 묶음 악보 곡 상세에서 사용자가 검색한 이름이
     * 화면 어디에도 없다(기획 §10-3). 그래서 이 값만 별도 패스로 채운다.
     *
     * <p>기록은 <b>결과와 무관하게</b> 남긴다(곡이 없든, 값이 이미 있든, 채웠든) — 그래야 다음 기동부터
     * 아무 일도 하지 않고, 관리자가 지운 문구가 되살아나지 않는다.
     */
    private int backfillCollectionGuides() {
        Set<String> alreadyBackfilled = new HashSet<>(seedLoadRepository.findNaturalKeys(SeedType.COLLECTION_GUIDE));
        int filled = 0;
        for (Map<String, String> row : csvReader.read(WORKS_CSV)) {
            String guide = value(row, "collection_guide");
            String canonicalUrl = ImslpUrlNormalizer.canonicalize(value(row, "imslp_url"));
            if (guide == null || canonicalUrl == null || alreadyBackfilled.contains(canonicalUrl)) {
                continue;
            }
            WorkEntity work = workRepository.findByImslpUrl(canonicalUrl).orElse(null);
            if (work != null && (work.getCollectionGuide() == null || work.getCollectionGuide().isBlank())) {
                work.fillCollectionGuideIfBlank(guide);
                filled++;
            }
            recordLoaded(SeedType.COLLECTION_GUIDE, canonicalUrl);
        }
        return filled;
    }

    private int loadComposers() {
        Set<String> alreadyLoaded = new HashSet<>(seedLoadRepository.findNaturalKeys(SeedType.COMPOSER));
        int inserted = 0;
        for (Map<String, String> row : csvReader.read(COMPOSERS_CSV)) {
            String nameOriginal = value(row, "name_original");
            if (nameOriginal == null) {
                continue;
            }
            String normalized = SearchNormalizer.normalize(nameOriginal);
            if (alreadyLoaded.contains(normalized)) {
                continue;
            }
            if (composerRepository.findByNameOriginalNormalized(normalized).isPresent()) {
                // 기록이 없는 기존 DB 의 첫 실행 — 중복을 만들지 않고 기록만 남긴다(01_ERD §3-10).
                recordLoaded(SeedType.COMPOSER, normalized);
                continue;
            }
            ComposerEntity composer = ComposerEntity.builder()
                    .nameKo(value(row, "name_ko"))
                    .nameOriginal(nameOriginal)
                    .birthYear(intValue(row, "birth_year"))
                    .deathYear(intValue(row, "death_year"))
                    .nationality(value(row, "nationality"))
                    .imslpUrl(ImslpUrlNormalizer.composerCategoryUrl(nameOriginal))
                    .build();
            composer.addAliases(SeedCsvReader.multi(value(row, "aliases"), MULTI_SEPARATOR));
            composerRepository.save(composer);
            recordLoaded(SeedType.COMPOSER, normalized);
            inserted++;
        }
        return inserted;
    }

    private int loadWorks() {
        Set<String> alreadyLoaded = new HashSet<>(seedLoadRepository.findNaturalKeys(SeedType.WORK));
        int inserted = 0;
        for (Map<String, String> row : csvReader.read(WORKS_CSV)) {
            String canonicalUrl = ImslpUrlNormalizer.canonicalize(value(row, "imslp_url"));
            if (canonicalUrl == null) {
                log.warn("시드 곡의 IMSLP 주소가 올바르지 않아 건너뜁니다: {}", value(row, "imslp_url"));
                continue;
            }
            if (alreadyLoaded.contains(canonicalUrl)) {
                // 한 번 적재한 행은 다시 적재하지 않는다 — 관리자가 지운 곡을 되살리지 않기 위해서다(01_ERD §3-10).
                continue;
            }
            if (workRepository.findByImslpUrl(canonicalUrl).isPresent()) {
                recordLoaded(SeedType.WORK, canonicalUrl);
                continue;
            }
            String composerOriginal = value(row, "composer_original");
            ComposerEntity composer = composerRepository
                    .findByNameOriginalNormalized(SearchNormalizer.normalize(composerOriginal))
                    .orElse(null);
            if (composer == null) {
                log.warn("시드 곡의 작곡가를 찾지 못해 건너뜁니다: {}", composerOriginal);
                continue;
            }
            WorkEntity work = WorkEntity.builder()
                    .composer(composer)
                    .titleKo(value(row, "title_ko"))
                    .titleOriginal(value(row, "title_original"))
                    .level(level(value(row, "level")))
                    .collectionGuide(value(row, "collection_guide"))
                    .imslpUrl(canonicalUrl)
                    .hidden(false)
                    .build();
            work.addCatalogNumbers(SeedCsvReader.multi(value(row, "catalog_numbers"), MULTI_SEPARATOR));
            work.addAliases(SeedCsvReader.multi(value(row, "aliases"), MULTI_SEPARATOR), AliasSource.SEED);
            workRepository.save(work);
            recordLoaded(SeedType.WORK, canonicalUrl);
            inserted++;
        }
        return inserted;
    }

    /** 적재 기록 남기기 (01_ERD §3-10) — INSERT 했든, 이미 있어 건너뛰었든 남긴다. */
    private void recordLoaded(SeedType seedType, String naturalKey) {
        seedLoadRepository.save(SeedLoadEntity.builder()
                .seedType(seedType)
                .naturalKey(naturalKey)
                .build());
    }

    private static Level level(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        for (Level level : List.of(Level.values())) {
            if (level.name().equalsIgnoreCase(raw.trim())) {
                return level;
            }
        }
        return null;
    }

    private static String value(Map<String, String> row, String key) {
        String raw = row.get(key);
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static Integer intValue(Map<String, String> row, String key) {
        String raw = value(row, key);
        if (raw == null) {
            return null;
        }
        try {
            return Integer.valueOf(raw);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
