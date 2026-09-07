package com.test.test.sheetmusic.seed;

import com.test.test.sheetmusic.common.ImslpUrlNormalizer;
import com.test.test.sheetmusic.common.SearchNormalizer;
import com.test.test.sheetmusic.composer.ComposerEntity;
import com.test.test.sheetmusic.composer.repository.ComposerRepository;
import com.test.test.sheetmusic.work.AliasSource;
import com.test.test.sheetmusic.work.Level;
import com.test.test.sheetmusic.work.WorkEntity;
import com.test.test.sheetmusic.work.repository.WorkRepository;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 시드 적재 (01_ERD §6, 03 §8) — <b>멱등·삽입 전용</b>.
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

    @Transactional
    public void load() {
        int composers = loadComposers();
        int works = loadWorks();
        log.info("시드 적재 완료 - 새 작곡가 {}명, 새 곡 {}곡", composers, works);
    }

    private int loadComposers() {
        int inserted = 0;
        for (Map<String, String> row : csvReader.read(COMPOSERS_CSV)) {
            String nameOriginal = value(row, "name_original");
            if (nameOriginal == null) {
                continue;
            }
            String normalized = SearchNormalizer.normalize(nameOriginal);
            if (composerRepository.findByNameOriginalNormalized(normalized).isPresent()) {
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
            inserted++;
        }
        return inserted;
    }

    private int loadWorks() {
        int inserted = 0;
        for (Map<String, String> row : csvReader.read(WORKS_CSV)) {
            String canonicalUrl = ImslpUrlNormalizer.canonicalize(value(row, "imslp_url"));
            if (canonicalUrl == null) {
                log.warn("시드 곡의 IMSLP 주소가 올바르지 않아 건너뜁니다: {}", value(row, "imslp_url"));
                continue;
            }
            if (workRepository.findByImslpUrl(canonicalUrl).isPresent()) {
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
                    .imslpUrl(canonicalUrl)
                    .hidden(false)
                    .build();
            work.addCatalogNumbers(SeedCsvReader.multi(value(row, "catalog_numbers"), MULTI_SEPARATOR));
            work.addAliases(SeedCsvReader.multi(value(row, "aliases"), MULTI_SEPARATOR), AliasSource.SEED);
            workRepository.save(work);
            inserted++;
        }
        return inserted;
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
