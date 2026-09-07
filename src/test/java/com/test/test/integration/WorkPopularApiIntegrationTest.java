package com.test.test.integration;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GET /api/works/popular — docs/설계/02_API_명세서.md §3-2.
 * 정렬 download_count DESC, created_at DESC, id DESC. limit 기본 10, 최대 20. 숨김 제외.
 */
class WorkPopularApiIntegrationTest extends SheetMusicFixtureSupport {

    @Test
    @DisplayName("기본 10개, WorkSummaryDTO 형태, matchedAlias 는 항상 null")
    void popular_default10() throws Exception {
        mockMvc.perform(get("/api/works/popular"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(10)))
                .andExpect(jsonPath("$.data[0].id").isNumber())
                .andExpect(jsonPath("$.data[0].titleOriginal").isString())
                .andExpect(jsonPath("$.data[0].composer.nameOriginal").isString())
                .andExpect(jsonPath("$.data[0].catalogNumbers").isArray())
                .andExpect(jsonPath("$.data[0].status").isString())
                .andExpect(jsonPath("$.data[*].matchedAlias").value(everyItem(nullValue())));
    }

    @Test
    @DisplayName("limit=5 → 5개, limit=50 → 최대 20개, limit=0 → 400")
    void popular_limit() throws Exception {
        mockMvc.perform(get("/api/works/popular").param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(5)));

        mockMvc.perform(get("/api/works/popular").param("limit", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(20)));

        mockMvc.perform(get("/api/works/popular").param("limit", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("기록이 없으면 최근 등록순: 방금 만든 곡 B, A 가 맨 앞 (created_at DESC, 동률 id DESC)")
    void popular_recentFirstWhenNoDownloads() throws Exception {
        Tokens admin = loginAdmin();
        long composerId = createComposer(admin, "인기테스트", "Popular, Zz");
        long a = createWork(admin, composerId, "zz인기 A", "Zz Popular A");
        long b = createWork(admin, composerId, "zz인기 B", "Zz Popular B");

        List<Long> ids = popularIds(10);
        assertThat(ids.get(0)).isEqualTo(b);
        assertThat(ids.get(1)).isEqualTo(a);
    }

    @Test
    @DisplayName("다운로드 수가 있으면 그 순서가 우선: A 를 2번 받으면 A 가 1위")
    void popular_downloadCountFirst() throws Exception {
        Tokens admin = loginAdmin();
        long composerId = createComposer(admin, "인기다운", "Populardl, Zz");
        ReadyWork a = createReadyWork(admin, composerId, "zz인기 받은곡", "Zz Popular Downloaded", 3);
        ReadyWork b = createReadyWork(admin, composerId, "zz인기 안받은곡", "Zz Popular Fresh", 3);

        // 등록만 했을 때는 최근 등록(b)이 앞
        assertThat(popularIds(10).get(0)).isEqualTo(b.workId());

        mockMvc.perform(get("/api/editions/{id}/download", a.editionId())).andExpect(status().isOk());
        mockMvc.perform(get("/api/editions/{id}/download", a.editionId())).andExpect(status().isOk());

        List<Long> ids = popularIds(10);
        assertThat(ids.get(0)).isEqualTo(a.workId());
        assertThat(ids.get(1)).isEqualTo(b.workId());
    }

    @Test
    @DisplayName("숨김 곡은 인기곡에 나오지 않는다")
    void popular_excludesHidden() throws Exception {
        Tokens admin = loginAdmin();
        long composerId = createComposer(admin, "인기숨김", "Popularhidden, Zz");
        long hidden = createWork(admin, composerId, "zz인기 숨김", "Zz Popular Hidden", List.of(), List.of(), null, true);

        assertThat(popularIds(20)).doesNotContain(hidden);
    }

    private List<Long> popularIds(int limit) throws Exception {
        JsonNode data = getJson("/api/works/popular?limit=" + limit).path("data");
        List<Long> ids = new ArrayList<>();
        data.forEach(w -> ids.add(w.path("id").asLong()));
        return ids;
    }
}
