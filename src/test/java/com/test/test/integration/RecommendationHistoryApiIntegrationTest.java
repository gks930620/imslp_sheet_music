package com.test.test.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.test.test.integration.support.AdminApiTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 이력 "더 보기"(02 §4-7-1) · 곡 목록의 자동/사람 구분(§4-6) · <b>사용자 응답 무변화</b>(기획 06 §6, 8-F 1).
 *
 * <p>세 가지를 한 자리에 두는 이유: 전부 "근거를 <b>어디서 읽는가</b>" 의 계약이기 때문이다.
 * 관리자는 곡 편집(§4-7-2)·이력 전체(§4-7-1)·곡 목록(§4-6) 세 자리에서 읽고,
 * <b>사용자는 어디서도 읽지 않는다</b> — 근거를 사용자 API 로 흘리면 1차 제외 확정(기획 §6)이 깨진다.
 */
class RecommendationHistoryApiIntegrationTest extends AdminApiTestSupport {

    private static final String HISTORY_URL = "/api/admin/works/{workId}/recommendation-history";

    // ===== §4-7-1 더 보기 =====

    @Test
    @DisplayName("전용 API 는 이력을 전부 최신순으로 준다 — 곡 상세는 5줄이었다")
    void historyApiReturnsEveryLine() throws Exception {
        Tokens admin = loginAdmin();
        long workId = createWork(admin, createComposer(admin));
        for (int i = 0; i < 7; i++) {
            long editionId = createFileEdition(admin, workId, "FREE", "판정 근거");
            setRecommended(admin, workId, recommendBody(editionId, "BETTER_READABILITY", null, null));
        }

        assertThat(recommendation(admin, workId).path("history").size()).isEqualTo(5);

        JsonNode result = data(adminGet(admin, HISTORY_URL, workId).andExpect(status().isOk()));
        assertThat(result.path("workId").asLong()).isEqualTo(workId);
        assertThat(result.path("historyCount").asInt()).isEqualTo(7);
        assertThat(result.path("history").size()).isEqualTo(7);
        // 최신이 맨 위 — 곡 상세의 맨 위 줄과 같은 줄이다
        assertThat(result.path("history").get(0).path("id").asLong())
                .isEqualTo(recommendation(admin, workId).path("current").path("id").asLong());
    }

    @Test
    @DisplayName("기록이 없는 곡도 200 이고 빈 배열이다 — 없음을 오류로 만들지 않는다")
    void emptyHistoryIsAnEmptyArray() throws Exception {
        Tokens admin = loginAdmin();
        long workId = createWork(admin, createComposer(admin));

        JsonNode result = data(adminGet(admin, HISTORY_URL, workId).andExpect(status().isOk()));
        assertThat(result.path("historyCount").asInt()).isZero();
        assertThat(result.path("history").isArray()).isTrue();
        assertThat(result.path("history").size()).isZero();
    }

    @Test
    @DisplayName("없는 곡 404 · 비로그인 401 · USER 403")
    void historyApiErrorPaths() throws Exception {
        Tokens admin = loginAdmin();
        long workId = createWork(admin, createComposer(admin));

        adminGet(admin, HISTORY_URL, 99_999_999L).andExpect(status().isNotFound());
        mockMvc.perform(get(HISTORY_URL, workId)).andExpect(status().isUnauthorized());
        adminGet(loginUser(), HISTORY_URL, workId).andExpect(status().isForbidden());
    }

    // ===== §4-6 곡 목록의 자동/사람/기록 없음 =====

    @Test
    @DisplayName("사람이 고른 곡은 ADMIN, 자동으로 지정된 곡은 AUTO 다 (8-A 7)")
    void listTellsWhoChose() throws Exception {
        Tokens admin = loginAdmin();
        long composerId = createComposer(admin);

        long byAdmin = createWork(admin, composerId);
        setRecommended(admin, byAdmin, createFileEdition(admin, byAdmin, "FREE", "판정 근거"));

        long byAuto = createWork(admin, composerId);
        createFileEdition(admin, byAuto, "FREE", "판정 근거");
        adminPost(admin, "/api/admin/copyright/auto-judge", json("dryRun", false, "assignRecommended", true))
                .andExpect(status().isOk());

        assertThat(summaryOf(admin, byAdmin).path("recommendationSource").asText()).isEqualTo("ADMIN");
        assertThat(summaryOf(admin, byAuto).path("recommendationSource").asText()).isEqualTo("AUTO");
    }

    @Test
    @DisplayName("추천은 있는데 기록이 없으면 null — 화면이 '기록 없음' 으로 그리는 자리다 (실데이터 42곡)")
    void aRecommendationWithoutARecordHasNoSource() throws Exception {
        Tokens admin = loginAdmin();
        long workId = createWork(admin, createComposer(admin));
        makeReady(admin, workId);
        clearRecommendationLog(workId);

        JsonNode summary = summaryOf(admin, workId);
        assertThat(summary.path("hasRecommended").asBoolean()).isTrue();
        assertThat(summary.path("recommendationSource").isNull()).isTrue();
    }

    @Test
    @DisplayName("추천이 없는 곡은 언제나 null — 이력이 남아 있어도 그렇다 (화면은 '–')")
    void aWorkWithoutARecommendationHasNoSource() throws Exception {
        Tokens admin = loginAdmin();
        long composerId = createComposer(admin);

        long never = createWork(admin, composerId);
        assertThat(summaryOf(admin, never).path("recommendationSource").isNull()).isTrue();

        long cleared = createWork(admin, composerId);
        long editionId = createFileEdition(admin, cleared, "FREE", "판정 근거");
        setRecommended(admin, cleared, editionId);
        adminDelete(admin, "/api/admin/editions/{id}", editionId).andExpect(status().isNoContent());

        JsonNode summary = summaryOf(admin, cleared);
        assertThat(summary.path("hasRecommended").asBoolean()).isFalse();
        assertThat(summary.path("recommendationSource").isNull()).isTrue();
    }

    @Test
    @DisplayName("근거 때문에 새 상태 필터가 생기지 않았다 (8-E 4) — 모르는 status 는 그대로 400 이다")
    void noNewStatusFilterWasAdded() throws Exception {
        Tokens admin = loginAdmin();

        adminQuery(admin, "/api/admin/works", "status", "NO_RECOMMENDATION_RECORD")
                .andExpect(status().isBadRequest());
    }

    // ===== 사용자 응답은 한 글자도 바뀌지 않는다 (기획 §6, 8-F 1) =====

    @Test
    @DisplayName("사용자 곡 상세·검색에 근거가 흘러나오지 않는다 — 1차 제외는 계약으로 지킨다")
    void userFacingResponsesCarryNoEvidence() throws Exception {
        Tokens admin = loginAdmin();
        long workId = createWork(admin, createComposer(admin));
        long editionId = createFileEdition(admin, workId, "FREE", "판정 근거");
        setRecommended(admin, workId, recommendBody(editionId, "NOT_THIS_WORK", "앞 추천은 관현악 총보였음", null));

        String detail = mockMvc.perform(get("/api/works/{id}", workId))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String search = mockMvc.perform(get("/api/works/search").param("q", "테스트곡"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        for (String body : new String[]{detail, search}) {
            assertThat(body).doesNotContain("\"recommendation\"");
            assertThat(body).doesNotContain("decidedByNickname");
            assertThat(body).doesNotContain("candidateCount");
            assertThat(body).doesNotContain("MOST_IMSLP_DOWNLOADS");
            assertThat(body).doesNotContain("NOT_THIS_WORK");
            assertThat(body).doesNotContain("앞 추천은 관현악 총보였음");
        }
    }

    /** §4-6 목록에서 그 곡 한 줄을 집는다 (목록 계약을 목록으로 확인한다 — 상세로 대신하지 않는다). */
    private JsonNode summaryOf(Tokens admin, long workId) throws Exception {
        JsonNode result = data(adminQuery(admin, "/api/admin/works", "size", "200").andExpect(status().isOk()));
        for (JsonNode row : result.path("works").path("content")) {
            if (row.path("id").asLong() == workId) {
                return row;
            }
        }
        throw new AssertionError("곡 목록에 " + workId + " 가 없다");
    }
}
