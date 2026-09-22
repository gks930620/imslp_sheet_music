package com.test.test.integration;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 작곡가 공개 API — docs/설계/02_API_명세서.md §3-5 ~ §3-8.
 *
 * <p>시드 규모(작곡가·곡 수)는 계속 커지는 값이라 <b>여기서는 하드코딩하지 않는다</b> — 로더가 실제로 읽는
 * {@code seed/composers.csv}·{@code seed/works.csv} 를 {@link #seedCsvReader} 로 직접 세어 기대값을 만든다
 * (컴포저 총수·특정 작곡가 곡 수는 {@code SheetMusicFixtureSupport} 의 {@code seedComposerCount()}·
 * {@code seedWorkCountFor(...)}). 시드가 늘어도 이 파일은 다시 손댈 필요가 없다 — 잠그는 것은
 * "정렬·필터가 CSV 내용과 일치하는가" 지 "지금 몇 곡인가" 가 아니다.
 */
class ComposerApiIntegrationTest extends SheetMusicFixtureSupport {

    private static final String CHOPIN = "Chopin, Frédéric";

    // ===== 3-5 전체 목록 =====

    @Test
    @DisplayName("GET /api/composers: 공개 곡 1개 이상인 시드 작곡가 전원, name_ko 가나다순, 필드 6개")
    void list_seedComposersSortedByNameKo() throws Exception {
        int expectedTotal = seedComposerCount();
        JsonNode data = getJson("/api/composers").path("data");
        assertThat(data.path("total").asInt()).isEqualTo(expectedTotal);
        JsonNode composers = data.path("composers");
        assertThat(composers).hasSize(expectedTotal);

        List<String> names = new ArrayList<>();
        JsonNode grieg = null;
        for (JsonNode c : composers) {
            assertThat(c.path("id").isNumber()).isTrue();
            assertThat(c.path("nameKo").isTextual()).isTrue();
            assertThat(c.path("nameOriginal").isTextual()).isTrue();
            assertThat(c.has("birthYear")).isTrue();
            assertThat(c.has("deathYear")).isTrue();
            assertThat(c.path("workCount").asInt()).isGreaterThanOrEqualTo(1);
            names.add(c.path("nameKo").asText());
            if ("Grieg, Edvard".equals(c.path("nameOriginal").asText())) {
                grieg = c;
            }
        }
        List<String> sorted = new ArrayList<>(names);
        sorted.sort(String::compareTo); // 한글 음절 코드포인트 순 = 가나다 순
        assertThat(names).isEqualTo(sorted);

        // 특정 작곡가 하나는 위치가 아니라 내용으로 잠근다 — 알파벳 순 첫/끝 작곡가는 시드가 늘면 바뀐다.
        assertThat(grieg).as("시드에 그리그가 있어야 한다").isNotNull();
        assertThat(grieg.path("birthYear").asInt()).isEqualTo(1843);
        assertThat(grieg.path("deathYear").asInt()).isEqualTo(1907);
        assertThat(grieg.path("workCount").asInt()).isEqualTo(seedWorkCountFor("Grieg, Edvard"));
    }

    @Test
    @DisplayName("공개 곡이 0개(곡 없음 / 숨김 곡만)인 작곡가는 목록에 없다")
    void list_excludesComposersWithoutPublicWorks() throws Exception {
        int before = seedComposerCount();
        Tokens admin = loginAdmin();
        long noWorks = createComposer(admin, "곡없음", "Noworks, Zz");
        long hiddenOnly = createComposer(admin, "숨김만", "Hiddenonly, Zz");
        createWork(admin, hiddenOnly, "zz숨김", "Zz Hidden", List.of(), List.of(), null, true);
        long visible = createComposer(admin, "곡있음", "Hasworks, Zz");
        createWork(admin, visible, "zz공개", "Zz Visible");

        JsonNode data = getJson("/api/composers").path("data");
        assertThat(data.path("total").asInt()).isEqualTo(before + 1);
        List<Long> ids = new ArrayList<>();
        data.path("composers").forEach(c -> ids.add(c.path("id").asLong()));
        assertThat(ids).contains(visible).doesNotContain(noWorks, hiddenOnly);
    }

    // ===== 3-6 featured =====

    @Test
    @DisplayName("GET /api/composers/featured: 기본 8명, 곡 수 내림차순(동률 name_ko 순), 쇼팽이 1위")
    void featured_default8ByWorkCount() throws Exception {
        // 기대값은 CSV 를 직접 세어 만든다 — 곡을 더 큐레이션해도 이 테스트는 다시 손댈 필요가 없다.
        List<ComposerWorkCount> expected = topComposersByWorkCount(8);
        assertThat(expected).hasSize(8);

        JsonNode data = getJson("/api/composers/featured").path("data");
        assertThat(data).hasSize(8);
        // 쇼팽이 피아노 독주곡 시드에서 가장 많이 큐레이션된 작곡가라는 사실은 그대로 잠근다.
        assertThat(expected.get(0).nameOriginal()).isEqualTo(CHOPIN);
        for (int i = 0; i < 8; i++) {
            assertThat(data.get(i).path("nameKo").asText()).isEqualTo(expected.get(i).nameKo());
            assertThat(data.get(i).path("nameOriginal").asText()).isEqualTo(expected.get(i).nameOriginal());
            assertThat(data.get(i).path("workCount").asInt()).isEqualTo(expected.get(i).workCount());
        }
        for (int i = 1; i < data.size(); i++) {
            int prev = data.get(i - 1).path("workCount").asInt();
            int cur = data.get(i).path("workCount").asInt();
            assertThat(cur).isLessThanOrEqualTo(prev);
            if (cur == prev) {
                assertThat(data.get(i).path("nameKo").asText().compareTo(data.get(i - 1).path("nameKo").asText()))
                        .as("동률이면 name_ko 순").isGreaterThan(0);
            }
        }
        // ComposerCardDTO 필드 4개
        assertThat(data.get(0).fieldNames()).toIterable()
                .containsExactlyInAnyOrder("id", "nameKo", "nameOriginal", "workCount");
    }

    private record ComposerWorkCount(String nameKo, String nameOriginal, int workCount) {
    }

    /** CSV 를 직접 세어 곡 수 내림차순(동률이면 name_ko 오름차순) 상위 n명을 만든다 — featured 정렬의 독립 기대값. */
    private List<ComposerWorkCount> topComposersByWorkCount(int limit) {
        Map<String, Integer> counts = new HashMap<>();
        for (Map<String, String> row : seedWorkRows()) {
            counts.merge(row.get("composer_original"), 1, Integer::sum);
        }
        List<ComposerWorkCount> all = new ArrayList<>();
        for (Map<String, String> row : seedComposerRows()) {
            String original = row.get("name_original");
            Integer count = counts.get(original);
            if (count != null && count > 0) {
                all.add(new ComposerWorkCount(row.get("name_ko"), original, count));
            }
        }
        all.sort(Comparator.comparingInt(ComposerWorkCount::workCount).reversed()
                .thenComparing(ComposerWorkCount::nameKo));
        return all.size() <= limit ? all : all.subList(0, limit);
    }

    @Test
    @DisplayName("featured limit=3 → 3명, limit=50 → 최대 20명")
    void featured_limit() throws Exception {
        mockMvc.perform(get("/api/composers/featured").param("limit", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(3)));
        mockMvc.perform(get("/api/composers/featured").param("limit", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(20)));
    }

    // ===== 3-7 상세 =====

    @Test
    @DisplayName("GET /api/composers/{id}: 쇼팽 상세 — 생몰년·국적·별칭·IMSLP 링크·workCount")
    void detail_chopin() throws Exception {
        long id = findSeedComposerId(CHOPIN);
        mockMvc.perform(get("/api/composers/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(id))
                .andExpect(jsonPath("$.data.nameKo").value("쇼팽"))
                .andExpect(jsonPath("$.data.nameOriginal").value(CHOPIN))
                .andExpect(jsonPath("$.data.birthYear").value(1810))
                .andExpect(jsonPath("$.data.deathYear").value(1849))
                .andExpect(jsonPath("$.data.nationality").value("폴란드"))
                .andExpect(jsonPath("$.data.aliases").isArray())
                .andExpect(jsonPath("$.data.imslpUrl").value("https://imslp.org/wiki/Category:Chopin,_Frédéric"))
                .andExpect(jsonPath("$.data.workCount").value(seedWorkCountFor(CHOPIN)));

        List<String> aliases = new ArrayList<>();
        getJson("/api/composers/{id}", id).path("data").path("aliases").forEach(a -> aliases.add(a.asText()));
        assertThat(aliases).contains("프레데리크 쇼팽", "쇼팡", "Chopin", "Fryderyk Chopin");
    }

    @Test
    @DisplayName("공개 곡 0개인 작곡가도 상세는 200 (workCount 0)")
    void detail_zeroWorks_200() throws Exception {
        Tokens admin = loginAdmin();
        long id = createComposer(admin, "곡없음상세", "Noworksdetail, Zz");
        mockMvc.perform(get("/api/composers/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.workCount").value(0));
    }

    @Test
    @DisplayName("없는 작곡가 → 404 NOT_FOUND")
    void detail_notFound() throws Exception {
        mockMvc.perform(get("/api/composers/{id}", 999_999_999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("NOT_FOUND"));
    }

    // ===== 3-8 작곡가의 곡 =====

    @Test
    @DisplayName("GET /api/composers/{id}/works: 기본 정렬 downloads(download_count DESC, id ASC) — 기록 없으면 id 순")
    void works_defaultSort() throws Exception {
        long id = findSeedComposerId(CHOPIN);
        int expectedCount = seedWorkCountFor(CHOPIN);
        JsonNode data = getJson("/api/composers/{id}/works", id).path("data");
        assertThat(data.path("unfilteredTotal").asInt()).isEqualTo(expectedCount);
        JsonNode content = data.path("works").path("content");
        assertThat(content).hasSize(expectedCount);
        assertThat(data.path("works").path("totalElements").asInt()).isEqualTo(expectedCount);

        List<Long> ids = new ArrayList<>();
        content.forEach(w -> ids.add(w.path("id").asLong()));
        List<Long> sorted = new ArrayList<>(ids);
        sorted.sort(Long::compareTo);
        assertThat(ids).isEqualTo(sorted);
        for (JsonNode w : content) {
            assertThat(w.path("composer").path("id").asLong()).isEqualTo(id);
            assertThat(w.path("matchedAlias").isNull()).isTrue();
        }
    }

    @Test
    @DisplayName("sort=opus: 대표 작품번호 sort_key 순 (문자 접두사 → 숫자 오름차순, 예: B.49 < B.150 < Op.9 < Op.10)")
    void works_sortByOpus() throws Exception {
        long id = findSeedComposerId(CHOPIN);
        JsonNode content = getJson("/api/composers/{id}/works?sort=opus", id)
                .path("data").path("works").path("content");
        assertThat(content).hasSize(seedWorkCountFor(CHOPIN));

        List<String> catalogNumbers = new ArrayList<>();
        content.forEach(w -> catalogNumbers.add(w.path("catalogNumbers").get(0).asText()));

        // 독립 오라클: 구현의 CatalogSortKey(문자열 패딩)를 그대로 베끼지 않고, "문자 접두사 → 숫자" 로
        // 별도 계산해 비교한다 — 시드가 늘어 작품번호가 추가돼도 이 목록은 그대로 통과해야 한다.
        List<String> expected = new ArrayList<>(catalogNumbers);
        expected.sort(Comparator.comparing(ComposerApiIntegrationTest::catalogPrefix)
                .thenComparingInt(ComposerApiIntegrationTest::catalogNumberValue));
        assertThat(catalogNumbers).as("작품번호가 접두사·숫자 순으로 와야 한다").isEqualTo(expected);

        // 구체적 사실은 그대로 잠근다: 유작(B.) 은 정식 작품번호(Op.) 보다 항상 앞서고, Op.9 는 Op.10 보다 앞선다.
        assertThat(catalogNumbers.get(0)).startsWith("B.");
        assertThat(catalogNumbers).contains("Op.9", "Op.10");
        assertThat(catalogNumbers.indexOf("Op.9")).isLessThan(catalogNumbers.indexOf("Op.10"));
    }

    /** "B.150" → "B.", "Op.9" → "Op." — 첫 숫자 앞까지. */
    private static String catalogPrefix(String catalogNumber) {
        int i = 0;
        while (i < catalogNumber.length() && !Character.isDigit(catalogNumber.charAt(i))) {
            i++;
        }
        return catalogNumber.substring(0, i);
    }

    /** "B.150" → 150, "Op.9" → 9 — 접두사 뒤 숫자 구간. */
    private static int catalogNumberValue(String catalogNumber) {
        int i = 0;
        while (i < catalogNumber.length() && !Character.isDigit(catalogNumber.charAt(i))) {
            i++;
        }
        int start = i;
        while (i < catalogNumber.length() && Character.isDigit(catalogNumber.charAt(i))) {
            i++;
        }
        return Integer.parseInt(catalogNumber.substring(start, i));
    }

    @Test
    @DisplayName("sort=opus: 작품번호 없는 곡은 뒤로(NULLS LAST), 그 안에서는 title_original 순 — 사티")
    void works_sortByOpus_nullsLast() throws Exception {
        long id = findSeedComposerId("Satie, Erik");
        JsonNode content = getJson("/api/composers/{id}/works?sort=opus", id)
                .path("data").path("works").path("content");

        List<String> expectedTitles = new ArrayList<>();
        for (Map<String, String> row : seedWorkRows()) {
            if ("Satie, Erik".equals(row.get("composer_original"))) {
                assertThat(row.get("catalog_numbers"))
                        .as("이 테스트는 작품번호 없는 곡으로 NULLS LAST 를 본다 — 사티 시드에 작품번호가 생기면 다른 작곡가로 바꿔야 한다")
                        .isBlank();
                expectedTitles.add(row.get("title_original"));
            }
        }
        expectedTitles.sort(String::compareTo);

        List<String> titles = new ArrayList<>();
        content.forEach(w -> titles.add(w.path("titleOriginal").asText()));
        assertThat(titles).isEqualTo(expectedTitles);
    }

    @Test
    @DisplayName("sort 에 정의되지 않은 값 → 400")
    void works_invalidSort_400() throws Exception {
        long id = findSeedComposerId(CHOPIN);
        mockMvc.perform(get("/api/composers/{id}/works", id).param("sort", "title"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("필터는 검색과 동일: level=ELEMENTARY → 쇼팽 1곡(왈츠 A단조); downloadable=true → 0")
    void works_filters() throws Exception {
        long id = findSeedComposerId(CHOPIN);
        int expectedTotal = seedWorkCountFor(CHOPIN);
        mockMvc.perform(get("/api/composers/{id}/works", id).param("level", "ELEMENTARY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unfilteredTotal").value(expectedTotal))
                .andExpect(jsonPath("$.data.works.totalElements").value(1))
                .andExpect(jsonPath("$.data.works.content[0].titleOriginal").value("Waltz in A minor, B.150"));

        mockMvc.perform(get("/api/composers/{id}/works", id).param("downloadable", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unfilteredTotal").value(expectedTotal))
                .andExpect(jsonPath("$.data.works.totalElements").value(0));

        mockMvc.perform(get("/api/composers/{id}/works", id).param("pages", "LE10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.works.totalElements").value(0));

        mockMvc.perform(get("/api/composers/{id}/works", id).param("pages", "BAD"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("페이지: size=4 — 첫 페이지는 4개(last=false), 마지막 페이지는 나머지(last=true)")
    void works_paging() throws Exception {
        long id = findSeedComposerId(CHOPIN);
        int total = seedWorkCountFor(CHOPIN);
        int size = 4;
        int totalPages = (total + size - 1) / size; // ceil
        int lastPageIndex = totalPages - 1;
        int lastPageSize = total - lastPageIndex * size;
        assertThat(lastPageIndex).as("이 테스트는 마지막 페이지가 꽉 차지 않아야 last=true 를 의미 있게 본다").isGreaterThan(0);
        assertThat(lastPageSize).isLessThan(size);

        mockMvc.perform(get("/api/composers/{id}/works", id).param("size", String.valueOf(size)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.works.content", hasSize(size)))
                .andExpect(jsonPath("$.data.works.size").value(size))
                .andExpect(jsonPath("$.data.works.totalPages").value(totalPages))
                .andExpect(jsonPath("$.data.works.last").value(false));
        mockMvc.perform(get("/api/composers/{id}/works", id)
                        .param("size", String.valueOf(size)).param("page", String.valueOf(lastPageIndex)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.works.content", hasSize(lastPageSize)))
                .andExpect(jsonPath("$.data.works.last").value(true));
    }

    @Test
    @DisplayName("숨김 곡은 작곡가 곡 목록·workCount 에서 제외")
    void works_excludesHidden() throws Exception {
        Tokens admin = loginAdmin();
        long id = createComposer(admin, "숨김목록", "Hiddenlist, Zz");
        long visible = createWork(admin, id, "zz보임", "Zz Visible");
        createWork(admin, id, "zz숨김", "Zz Hidden", List.of(), List.of(), null, true);

        mockMvc.perform(get("/api/composers/{id}/works", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unfilteredTotal").value(1))
                .andExpect(jsonPath("$.data.works.totalElements").value(1))
                .andExpect(jsonPath("$.data.works.content[0].id").value(visible));
        mockMvc.perform(get("/api/composers/{id}", id))
                .andExpect(jsonPath("$.data.workCount").value(1));
    }

    @Test
    @DisplayName("없는 작곡가의 곡 목록 → 404")
    void works_composerNotFound() throws Exception {
        mockMvc.perform(get("/api/composers/{id}/works", 999_999_999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("NOT_FOUND"));
    }
}
