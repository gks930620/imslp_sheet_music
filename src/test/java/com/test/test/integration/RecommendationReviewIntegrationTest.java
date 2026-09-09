package com.test.test.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.test.test.integration.support.AdminApiTestSupport;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * "추천 판본 미검수" — 02_API_명세서 §4-1 · §4-6 · §5-6-1, 01_ERD, 기획 §F6-4 · §8-17 · §10-8
 * (2026-09-08 계약 신설, senior-dev. qa 4차 결함 6. <b>Red</b>).
 *
 * <h2>인수 조건이 있는데 계약이 0건이었다</h2>
 * 기획 §10-8 의 근거: 자동 추천 지정은 "전체 악보 · 전곡" 만 보므로 <b>관현악 총보가 추천이 될 수 있다</b>
 * (파반느·사계·짐노페디·어린이 차지·꽃노래·엔터테이너 — 실제 큐레이션 50곡에서 나온 사례).
 * 그래서 기획은 §F6-4 (B)3 에 "추천 판본의 미리보기를 열어 피아노 악보가 맞는지 확인 → <b>확인함</b>" 을 두고,
 * §8-17 <b>공개(출시) 기준</b>에 "추천 판본 미검수 0곡" 을 넣었다. 그런데 계약·코드·화면정의 어디에도
 * 이 개념이 없었다(qa 4차: 전체 0건). <b>출시를 막는 조건인데 아무도 셀 수 없는 숫자</b>였다.
 *
 * <h2>확정 계약</h2>
 * <ul>
 *   <li><b>저장</b>(01_ERD {@code work}) — {@code recommended_edition_reviewed BOOLEAN NOT NULL DEFAULT FALSE}.
 *       "누가·언제" 는 남기지 않는다. 이 값이 답하는 질문은 <b>"지금 추천이 사람 눈을 통과했나"</b> 하나뿐이고,
 *       그 답은 추천이 바뀌는 순간 무효가 되므로 이력이 아니라 상태다.</li>
 *   <li><b>검수 대상</b> — {@code recommendedEditionId != null} 인 곡. 추천이 없으면 검수할 것이 없다.</li>
 *   <li><b>false 로 돌아가는 때</b> — 추천이 <b>바뀌거나 해제되면</b>(§5-6 · §5-11 자동 지정 포함) 자동으로 false.
 *       "그 판본을 봤다" 가 아니라 "지금 추천을 봤다" 이므로, 추천이 달라지면 확인은 무효다.
 *       {@code work} 의 다른 필드(제목·난이도 등) 수정으로는 바뀌지 않는다.</li>
 *   <li><b>true 로 바꾸는 유일한 길</b> — {@code PUT /api/admin/works/{workId}/recommended-edition/review}
 *       body {@code { "reviewed": true }} → 200 {@code AdminWorkDetailDTO}. {@code false} 로 되돌리기도 같은 API.
 *       추천이 없는 곡에 {@code true} 는 400 {@code VALIDATION_ERROR}.</li>
 *   <li><b>보이는 곳</b> — {@code AdminWorkDetailDTO}·{@code AdminWorkSummaryDTO} 의 {@code recommendationReviewed},
 *       곡 목록 필터 {@code status=NEEDS_RECOMMENDATION_REVIEW}, 관리 홈 카드 {@code needsRecommendationReviewWorks}.</li>
 *   <li><b>"보완 필요"(01 §4)에는 넣지 않는다</b> — 보완 필요가 답하는 질문은 "이 곡을 사용자에게 <b>열어 주려면</b>
 *       뭐가 남았나"(기획 §11-1)인데, 미검수 곡은 <b>이미 열려 있다</b>(바로 받기 가능). 두 목록을 섞으면
 *       §8-17 의 두 조건("35곡 이상 바로 받기 가능" + "미검수 0곡")이 한 숫자로 뭉개져 진척을 볼 수 없다.</li>
 *   <li><b>대시보드 숫자는 숨김 곡을 뺀다</b> — 이 값은 공개 기준(§8-17)을 재는 지표이고, 숨긴 곡은 공개 대상이 아니다.
 *       (숨김을 포함하는 {@code totalWorks} 와 다른 이유다.)</li>
 * </ul>
 */
class RecommendationReviewIntegrationTest extends AdminApiTestSupport {

    private static final String REVIEW_URL = "/api/admin/works/{workId}/recommended-edition/review";
    private static final String NEEDS_REVIEW_STATUS = "NEEDS_RECOMMENDATION_REVIEW";

    @Test
    @DisplayName("추천을 지정하면 미검수 상태로 시작하고, 확인하면 검수됨이 된다")
    void recommendingStartsUnreviewedAndReviewMarksIt() throws Exception {
        Tokens admin = loginAdmin();
        long workId = createWork(admin, createComposer(admin));
        makeReady(admin, workId);

        assertThat(getWork(admin, workId).path("recommendationReviewed").asBoolean())
                .as("자동이든 수동이든, 추천이 막 정해진 곡은 아직 사람 눈을 통과하지 않았다")
                .isFalse();

        JsonNode reviewed = data(review(admin, workId, true).andExpect(status().isOk()));
        assertThat(reviewed.path("recommendationReviewed").asBoolean())
                .as("응답은 곡 상세(관리) 그대로 — 화면이 다시 조회하지 않아도 된다")
                .isTrue();
        assertThat(getWork(admin, workId).path("recommendationReviewed").asBoolean()).isTrue();

        review(admin, workId, false).andExpect(status().isOk());
        assertThat(getWork(admin, workId).path("recommendationReviewed").asBoolean())
                .as("되돌리기도 같은 API 로 — 잘못 눌렀을 때 빠져나갈 길이 없으면 관리자가 확인을 미룬다")
                .isFalse();
    }

    @Test
    @DisplayName("추천 판본이 바뀌면 확인은 무효가 된다 — 확인한 것은 그 판본이 아니라 지금 추천이다")
    void changingTheRecommendationInvalidatesTheReview() throws Exception {
        Tokens admin = loginAdmin();
        long workId = createWork(admin, createComposer(admin));
        makeReady(admin, workId);
        review(admin, workId, true).andExpect(status().isOk());

        long another = createFileEdition(admin, workId, "FREE", "다른 판본 판정 근거");
        setRecommended(admin, workId, another);

        assertThat(getWork(admin, workId).path("recommendationReviewed").asBoolean())
                .as("새 추천은 아직 아무도 안 봤다")
                .isFalse();
    }

    @Test
    @DisplayName("곡의 다른 필드를 고쳐도 확인은 유지된다 — 제목을 고쳤다고 악보가 달라지지 않는다")
    void editingOtherFieldsKeepsTheReview() throws Exception {
        Tokens admin = loginAdmin();
        long composerId = createComposer(admin);
        long workId = createWork(admin, composerId);
        makeReady(admin, workId);
        review(admin, workId, true).andExpect(status().isOk());

        JsonNode work = getWork(admin, workId);
        saveWork(admin, workId, workBody(composerId, "제목만 바꾼 곡 " + uniq(), text(work, "titleOriginal")));

        assertThat(getWork(admin, workId).path("recommendationReviewed").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("추천이 없는 곡은 확인할 것이 없다 → 400")
    void reviewWithoutRecommendationIsRejected() throws Exception {
        Tokens admin = loginAdmin();
        long workId = createWork(admin, createComposer(admin));

        review(admin, workId, true)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("관리 홈 카드와 곡 목록 필터가 같은 곡을 센다 — 확인하면 둘 다 함께 빠진다")
    void dashboardCardAndListFilterAgree() throws Exception {
        Tokens admin = loginAdmin();
        int before = dashboardNeedsReview(admin);

        long workId = createWork(admin, createComposer(admin));
        makeReady(admin, workId);

        assertThat(dashboardNeedsReview(admin))
                .as("§4-1 관리 홈 '추천 판본 확인 필요 N곡' 카드")
                .isEqualTo(before + 1);
        assertThat(needsReviewWorkIds(admin))
                .as("§4-6 곡 목록 필터 — 카드 숫자와 목록이 어긋나면 관리자는 어느 쪽도 믿지 않는다")
                .contains(workId);

        review(admin, workId, true).andExpect(status().isOk());

        assertThat(dashboardNeedsReview(admin)).isEqualTo(before);
        assertThat(needsReviewWorkIds(admin)).doesNotContain(workId);
    }

    @Test
    @DisplayName("숨긴 곡은 공개 기준 지표에서 빠진다 — 공개할 생각이 없는 곡은 출시를 막지 않는다")
    void hiddenWorkIsNotCounted() throws Exception {
        Tokens admin = loginAdmin();
        long composerId = createComposer(admin);
        long workId = createWork(admin, composerId);
        makeReady(admin, workId);
        int withVisible = dashboardNeedsReview(admin);

        JsonNode work = getWork(admin, workId);
        Map<String, Object> hide = workBody(composerId, text(work, "titleKo"), text(work, "titleOriginal"));
        hide.put("hidden", true);
        saveWork(admin, workId, hide);

        assertThat(dashboardNeedsReview(admin)).isEqualTo(withVisible - 1);
    }

    @Test
    @DisplayName("검수 API 는 관리자만 — 일반 사용자 403, 비로그인 401 (§0-3)")
    void reviewIsAdminOnly() throws Exception {
        Tokens admin = loginAdmin();
        long workId = createWork(admin, createComposer(admin));
        makeReady(admin, workId);
        String requestBody = body(json("reviewed", true));

        mockMvc.perform(put(REVIEW_URL, workId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(loginUser().accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isForbidden());

        mockMvc.perform(put(REVIEW_URL, workId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isUnauthorized());
    }

    // ===== 헬퍼 =====

    private ResultActions review(Tokens admin, long workId, boolean reviewed) throws Exception {
        return adminPut(admin, REVIEW_URL, json("reviewed", reviewed), workId);
    }

    private void saveWork(Tokens admin, long workId, Map<String, Object> body) throws Exception {
        adminPut(admin, "/api/admin/works/{id}", body, workId).andExpect(status().isOk());
    }

    private int dashboardNeedsReview(Tokens admin) throws Exception {
        JsonNode dashboard = data(adminGet(admin, "/api/admin/dashboard").andExpect(status().isOk()));
        assertThat(dashboard.has("needsRecommendationReviewWorks"))
                .as("§4-1 에 카드 값이 있어야 화면이 만들 수 있다: %s", dashboard)
                .isTrue();
        return dashboard.path("needsRecommendationReviewWorks").asInt();
    }

    private List<Long> needsReviewWorkIds(Tokens admin) throws Exception {
        JsonNode data = data(adminQuery(admin, "/api/admin/works", "status", NEEDS_REVIEW_STATUS, "size", "200")
                .andExpect(status().isOk()));
        return longs(data.path("works").path("content"), "id");
    }
}
