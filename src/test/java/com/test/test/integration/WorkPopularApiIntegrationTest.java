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
 * GET /api/works/popular — 02 §3-2 (기획 01 §11-3, 2026-09-08 전면 개정).
 *
 * <p>홈의 이 자리는 순위표가 아니라 <b>견본 진열대</b>다. 자격(바로 받기 가능 AND 한국어 대표 제목)을 갖춘 곡만 오르고,
 * 정렬은 다운로드 수 → 난이도 오름차순 → 가나다 → id. 모자란 칸만 준비 중 곡으로 채우되 자격 곡이 0개면 빈 배열이다.
 *
 * <p>시드 곡 50개는 판본이 없어 전부 PREPARING 이므로, 테스트 시작 시점에 자격 곡은 0개다(트랜잭션은 롤백된다).
 */
class WorkPopularApiIntegrationTest extends SheetMusicFixtureSupport {

    @Test
    @DisplayName("자격 곡이 0개면 빈 배열 — 준비 중 곡이 아무리 많아도 폴백하지 않는다 (화면은 영역을 숨긴다)")
    void popular_emptyWhenNoEligibleWork() throws Exception {
        Tokens admin = loginAdmin();
        long composerId = createComposer(admin, "인기없음", "Popularnone, Zz");
        createWork(admin, composerId, "zz준비중 곡", "Zz Preparing Only");   // 준비 중 곡을 더 만들어도

        mockMvc.perform(get("/api/works/popular"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(0)));
        mockMvc.perform(get("/api/works/popular").param("limit", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    @DisplayName("자격 곡이 1개면 그 곡이 맨 앞이고 모자란 칸만 준비 중 곡으로 채운다")
    void popular_fillsRemainingSlotsWithPreparingWorks() throws Exception {
        Tokens admin = loginAdmin();
        long composerId = createComposer(admin, "인기폴백", "Popularfallback, Zz");
        ReadyWork ready = createReadyWork(admin, composerId, "zz인기 자격곡", "Zz Eligible", 3);

        JsonNode data = getJson("/api/works/popular?limit=10").path("data");
        assertThat(data).hasSize(10);
        assertThat(data.get(0).path("id").asLong()).isEqualTo(ready.workId());
        assertThat(data.get(0).path("status").asText()).isEqualTo("READY");
        // 나머지 칸은 준비 중 곡이고, 전부 한국어 대표 제목이 있다
        for (int i = 1; i < data.size(); i++) {
            assertThat(data.get(i).path("status").asText()).isEqualTo("PREPARING");
            assertThat(data.get(i).path("titleKo").asText()).isNotBlank();
        }
    }

    @Test
    @DisplayName("한국어 대표 제목이 없는 곡은 바로 받기 가능해도 오르지 않는다")
    void popular_excludesWorkWithoutKoreanTitle() throws Exception {
        Tokens admin = loginAdmin();
        long composerId = createComposer(admin, "인기제목없음", "Popularnotitle, Zz");
        ReadyWork withTitle = createReadyWork(admin, composerId, "zz인기 한국어제목", "Zz With Korean Title", 3);
        ReadyWork withoutTitle = createWorkWithRecommendedEdition(admin, composerId, "", "Zz Without Korean Title",
                List.of(), List.of(), "INTERMEDIATE", 3, "FREE");

        List<Long> ids = popularIds(20);
        assertThat(ids).contains(withTitle.workId()).doesNotContain(withoutTitle.workId());
    }

    @Test
    @DisplayName("이용 제한 · 저작권 확인 중 곡은 자격도 폴백도 아니다")
    void popular_excludesRestrictedAndUnknown() throws Exception {
        Tokens admin = loginAdmin();
        long composerId = createComposer(admin, "인기제한", "Popularrestricted, Zz");
        ReadyWork eligible = createReadyWork(admin, composerId, "zz인기 자격곡", "Zz Eligible2", 3);
        ReadyWork restricted = createWorkWithRecommendedEdition(admin, composerId, "zz인기 제한곡", "Zz Restricted2",
                List.of(), List.of(), "INTERMEDIATE", 3, "RESTRICTED");
        ReadyWork unknown = createWorkWithRecommendedEdition(admin, composerId, "zz인기 확인중곡", "Zz Unknown2",
                List.of(), List.of(), "INTERMEDIATE", 3, "UNKNOWN");

        List<Long> ids = popularIds(20);
        assertThat(ids).contains(eligible.workId())
                .doesNotContain(restricted.workId())
                .doesNotContain(unknown.workId());
    }

    @Test
    @DisplayName("숨김 곡은 인기곡에 나오지 않는다")
    void popular_excludesHidden() throws Exception {
        Tokens admin = loginAdmin();
        long composerId = createComposer(admin, "인기숨김", "Popularhidden, Zz");
        createReadyWork(admin, composerId, "zz인기 보이는곡", "Zz Visible", 3);
        long hidden = createWork(admin, composerId, "zz인기 숨김", "Zz Popular Hidden", List.of(), List.of(), null, true);

        assertThat(popularIds(20)).doesNotContain(hidden);
    }

    @Test
    @DisplayName("동점이면 난이도 오름차순 → 한국어 제목 가나다 (최근 등록순은 쓰지 않는다)")
    void popular_tieBreakerIsLevelThenTitle() throws Exception {
        Tokens admin = loginAdmin();
        long composerId = createComposer(admin, "인기정렬", "Popularorder, Zz");
        // 등록 순서를 일부러 뒤섞는다 — 최근 등록순이면 이 순서의 역순이 된다
        ReadyWork advanced = ready(admin, composerId, "zz가고급", "Zz Advanced", "ADVANCED");
        ReadyWork beginnerNa = ready(admin, composerId, "zz나입문", "Zz Beginner Na", "BEGINNER");
        ReadyWork noLevel = ready(admin, composerId, "zz라미정", "Zz No Level", null);
        ReadyWork beginnerDa = ready(admin, composerId, "zz다입문", "Zz Beginner Da", "BEGINNER");

        assertThat(popularIds(10).subList(0, 4)).containsExactly(
                beginnerNa.workId(), beginnerDa.workId(), advanced.workId(), noLevel.workId());
    }

    @Test
    @DisplayName("다운로드 수가 1순위 — 난이도가 높아도 받은 곡이 앞선다")
    void popular_downloadCountWinsOverLevel() throws Exception {
        Tokens admin = loginAdmin();
        long composerId = createComposer(admin, "인기다운", "Populardl, Zz");
        ReadyWork advanced = ready(admin, composerId, "zz가고급 받은곡", "Zz Advanced Downloaded", "ADVANCED");
        ReadyWork beginner = ready(admin, composerId, "zz나입문 안받은곡", "Zz Beginner Fresh", "BEGINNER");

        assertThat(popularIds(10).subList(0, 2)).containsExactly(beginner.workId(), advanced.workId());

        mockMvc.perform(get("/api/editions/{id}/download", advanced.editionId())).andExpect(status().isOk());

        assertThat(popularIds(10).subList(0, 2)).containsExactly(advanced.workId(), beginner.workId());
    }

    @Test
    @DisplayName("limit=5 → 5개, limit=50 → 최대 20개, limit=0 → 400")
    void popular_limit() throws Exception {
        Tokens admin = loginAdmin();
        long composerId = createComposer(admin, "인기제한값", "Popularlimit, Zz");
        createReadyWork(admin, composerId, "zz인기 자격곡", "Zz Eligible3", 3);

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
    @DisplayName("항목은 WorkSummaryDTO 형태 — matchedAlias 는 항상 null, scopeNote 는 §2-2-1 규칙")
    void popular_itemShape() throws Exception {
        Tokens admin = loginAdmin();
        long composerId = createComposer(admin, "인기형태", "Popularshape, Zz");
        createReadyWork(admin, composerId, "zz인기 형태곡", "Zz Shape", 3);

        mockMvc.perform(get("/api/works/popular"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").isNumber())
                .andExpect(jsonPath("$.data[0].titleKo").isString())
                .andExpect(jsonPath("$.data[0].titleOriginal").isString())
                .andExpect(jsonPath("$.data[0].composer.nameOriginal").isString())
                .andExpect(jsonPath("$.data[0].catalogNumbers").isArray())
                .andExpect(jsonPath("$.data[0].status").value("READY"))
                .andExpect(jsonPath("$.data[0].scopeNote").value(nullValue()))
                .andExpect(jsonPath("$.data[*].matchedAlias").value(everyItem(nullValue())));
    }

    private ReadyWork ready(Tokens admin, long composerId, String titleKo, String titleOriginal, String level)
            throws Exception {
        return createWorkWithRecommendedEdition(admin, composerId, titleKo, titleOriginal,
                List.of(), List.of(), level, 3, "FREE");
    }

    private List<Long> popularIds(int limit) throws Exception {
        JsonNode data = getJson("/api/works/popular?limit=" + limit).path("data");
        List<Long> ids = new ArrayList<>();
        data.forEach(w -> ids.add(w.path("id").asLong()));
        return ids;
    }
}
