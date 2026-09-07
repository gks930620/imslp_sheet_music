package com.test.test.integration;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 작곡가 공개 API — docs/설계/02_API_명세서.md §3-5 ~ §3-8.
 * 시드 작곡가 25명(곡 50)을 기준으로 정렬·필터·404·400 을 검증한다.
 */
class ComposerApiIntegrationTest extends SheetMusicFixtureSupport {

    private static final String CHOPIN = "Chopin, Frédéric";

    // ===== 3-5 전체 목록 =====

    @Test
    @DisplayName("GET /api/composers: 공개 곡 1개 이상인 시드 작곡가 25명, name_ko 가나다순, 필드 6개")
    void list_seedComposersSortedByNameKo() throws Exception {
        JsonNode data = getJson("/api/composers").path("data");
        assertThat(data.path("total").asInt()).isEqualTo(25);
        JsonNode composers = data.path("composers");
        assertThat(composers).hasSize(25);

        List<String> names = new ArrayList<>();
        for (JsonNode c : composers) {
            assertThat(c.path("id").isNumber()).isTrue();
            assertThat(c.path("nameKo").isTextual()).isTrue();
            assertThat(c.path("nameOriginal").isTextual()).isTrue();
            assertThat(c.has("birthYear")).isTrue();
            assertThat(c.has("deathYear")).isTrue();
            assertThat(c.path("workCount").asInt()).isGreaterThanOrEqualTo(1);
            names.add(c.path("nameKo").asText());
        }
        List<String> sorted = new ArrayList<>(names);
        sorted.sort(String::compareTo); // 한글 음절 코드포인트 순 = 가나다 순
        assertThat(names).isEqualTo(sorted);
        assertThat(names.get(0)).isEqualTo("그리그");
        assertThat(names.get(24)).isEqualTo("하농");

        JsonNode grieg = composers.get(0);
        assertThat(grieg.path("nameOriginal").asText()).isEqualTo("Grieg, Edvard");
        assertThat(grieg.path("birthYear").asInt()).isEqualTo(1843);
        assertThat(grieg.path("deathYear").asInt()).isEqualTo(1907);
        assertThat(grieg.path("workCount").asInt()).isEqualTo(1);
    }

    @Test
    @DisplayName("공개 곡이 0개(곡 없음 / 숨김 곡만)인 작곡가는 목록에 없다")
    void list_excludesComposersWithoutPublicWorks() throws Exception {
        Tokens admin = loginAdmin();
        long noWorks = createComposer(admin, "곡없음", "Noworks, Zz");
        long hiddenOnly = createComposer(admin, "숨김만", "Hiddenonly, Zz");
        createWork(admin, hiddenOnly, "zz숨김", "Zz Hidden", List.of(), List.of(), null, true);
        long visible = createComposer(admin, "곡있음", "Hasworks, Zz");
        createWork(admin, visible, "zz공개", "Zz Visible");

        JsonNode data = getJson("/api/composers").path("data");
        assertThat(data.path("total").asInt()).isEqualTo(26);
        List<Long> ids = new ArrayList<>();
        data.path("composers").forEach(c -> ids.add(c.path("id").asLong()));
        assertThat(ids).contains(visible).doesNotContain(noWorks, hiddenOnly);
    }

    // ===== 3-6 featured =====

    @Test
    @DisplayName("GET /api/composers/featured: 기본 8명, 곡 수 내림차순(동률 name_ko 순), 쇼팽(10곡)이 1위")
    void featured_default8ByWorkCount() throws Exception {
        JsonNode data = getJson("/api/composers/featured").path("data");
        assertThat(data).hasSize(8);
        assertThat(data.get(0).path("nameKo").asText()).isEqualTo("쇼팽");
        assertThat(data.get(0).path("nameOriginal").asText()).isEqualTo(CHOPIN);
        assertThat(data.get(0).path("workCount").asInt()).isEqualTo(10);
        assertThat(data.get(1).path("nameKo").asText()).isEqualTo("베토벤");
        assertThat(data.get(1).path("workCount").asInt()).isEqualTo(5);
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
    @DisplayName("GET /api/composers/{id}: 쇼팽 상세 — 생몰년·국적·별칭·IMSLP 링크·workCount 10")
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
                .andExpect(jsonPath("$.data.workCount").value(10));

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
        JsonNode data = getJson("/api/composers/{id}/works", id).path("data");
        assertThat(data.path("unfilteredTotal").asInt()).isEqualTo(10);
        JsonNode content = data.path("works").path("content");
        assertThat(content).hasSize(10);
        assertThat(data.path("works").path("totalElements").asInt()).isEqualTo(10);

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
    @DisplayName("sort=opus: 대표 작품번호 sort_key 순 (B.49 < B.150 < Op.9 < Op.10 < … < Op.69)")
    void works_sortByOpus() throws Exception {
        long id = findSeedComposerId(CHOPIN);
        JsonNode content = getJson("/api/composers/{id}/works?sort=opus", id)
                .path("data").path("works").path("content");
        List<String> titles = new ArrayList<>();
        content.forEach(w -> titles.add(w.path("titleOriginal").asText()));
        assertThat(titles).containsExactly(
                "Nocturne in C-sharp minor, B.49",
                "Waltz in A minor, B.150",
                "Nocturnes, Op.9",
                "Études, Op.10",
                "Ballade No.1, Op.23",
                "Preludes, Op.28",
                "Polonaise in A-flat major, Op.53",
                "Waltzes, Op.64",
                "Fantaisie-impromptu, Op.66",
                "Waltzes, Op.69");
    }

    @Test
    @DisplayName("sort=opus: 작품번호 없는 곡은 뒤로(NULLS LAST), 그 안에서는 title_original 순 — 사티")
    void works_sortByOpus_nullsLast() throws Exception {
        long id = findSeedComposerId("Satie, Erik");
        JsonNode content = getJson("/api/composers/{id}/works?sort=opus", id)
                .path("data").path("works").path("content");
        List<String> titles = new ArrayList<>();
        content.forEach(w -> titles.add(w.path("titleOriginal").asText()));
        assertThat(titles).containsExactly("3 Gymnopédies", "Gnossiennes");
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
    @DisplayName("필터는 검색과 동일: level=ELEMENTARY → 쇼팽 1곡(왈츠 A단조), unfilteredTotal 10; downloadable=true → 0")
    void works_filters() throws Exception {
        long id = findSeedComposerId(CHOPIN);
        mockMvc.perform(get("/api/composers/{id}/works", id).param("level", "ELEMENTARY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unfilteredTotal").value(10))
                .andExpect(jsonPath("$.data.works.totalElements").value(1))
                .andExpect(jsonPath("$.data.works.content[0].titleOriginal").value("Waltz in A minor, B.150"));

        mockMvc.perform(get("/api/composers/{id}/works", id).param("downloadable", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unfilteredTotal").value(10))
                .andExpect(jsonPath("$.data.works.totalElements").value(0));

        mockMvc.perform(get("/api/composers/{id}/works", id).param("pages", "LE10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.works.totalElements").value(0));

        mockMvc.perform(get("/api/composers/{id}/works", id).param("pages", "BAD"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("페이지: size=4 → totalPages 3, page=2 는 2개(last=true)")
    void works_paging() throws Exception {
        long id = findSeedComposerId(CHOPIN);
        mockMvc.perform(get("/api/composers/{id}/works", id).param("size", "4"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.works.content", hasSize(4)))
                .andExpect(jsonPath("$.data.works.size").value(4))
                .andExpect(jsonPath("$.data.works.totalPages").value(3))
                .andExpect(jsonPath("$.data.works.last").value(false));
        mockMvc.perform(get("/api/composers/{id}/works", id).param("size", "4").param("page", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.works.content", hasSize(2)))
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
