package com.test.test.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.test.test.integration.support.AdminApiTestSupport;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 추천 지정의 <b>사유</b> — 02 §5-6 (2026-09-21 개정, 기획 06 §1-4·§3-1·§3-3).
 *
 * <p>이 기능의 핵심은 "왜 이 판본인가" 를 남기는 것이고, <b>사유 없이 바꿀 수 있게 두면 한 달 뒤 절반의 곡이
 * 다시 "누가 언제 바꿨는지만 있고 왜는 모름" 이 된다</b>(기획 §1-4). 그래서 사유는 필수이고,
 * 미충족이면 <b>저장 자체가 거부</b>된다 — 400 을 주고 추천은 그대로 둔다(인수 조건 8-B 2·4).
 *
 * <p>함께 잠그는 것: "미리보기를 확인했어요" 체크({@code reviewed})로 <b>그 자리에서 검수를 끝내는</b> 경로(§3-3).
 */
class RecommendReasonIntegrationTest extends AdminApiTestSupport {

    private static final String URL = "/api/admin/works/{workId}/recommended-edition";

    // ===== 사유 필수 =====

    @Test
    @DisplayName("사유를 고르지 않으면 400 이고 추천은 바뀌지 않는다 (8-B 2)")
    void reasonIsRequired_andNothingChanges() throws Exception {
        Tokens admin = loginAdmin();
        long workId = createWork(admin, createComposer(admin));
        long first = makeReady(admin, workId);
        long second = createFileEdition(admin, workId, "FREE", "판정 근거");

        adminPut(admin, URL, recommendBody(second, null, null, null), workId)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("reason"));

        // 저장 거부 = 아무것도 바뀌지 않았다
        assertThat(getWork(admin, workId).path("recommendedEditionId").asLong()).isEqualTo(first);
    }

    @Test
    @DisplayName("모르는 사유 값도 400 — 계약에 없는 코드를 받아 두면 화면에 그릴 문구가 없다")
    void unknownReasonIsRejected() throws Exception {
        Tokens admin = loginAdmin();
        long workId = createWork(admin, createComposer(admin));
        long editionId = createFileEdition(admin, workId, "FREE", "판정 근거");

        adminPut(admin, URL, recommendBody(editionId, "BECAUSE_I_SAID_SO", null, null), workId)
                .andExpect(status().isBadRequest());
        assertThat(getWork(admin, workId).path("recommendedEditionId").isNull()).isTrue();
    }

    @Test
    @DisplayName("사유 6가지가 전부 받아들여진다 — 선언 순서가 화면 나열 순서다")
    void allSixReasonsAreAccepted() throws Exception {
        Tokens admin = loginAdmin();
        long composerId = createComposer(admin);
        String[] reasons = {"NOT_THIS_WORK", "BETTER_READABILITY", "BETTER_FOR_LEARNERS",
                "PREVIOUS_UNAVAILABLE", "COVERS_WHOLE_WORK", "OTHER"};

        for (String reason : reasons) {
            long workId = createWork(admin, composerId);
            long editionId = createFileEdition(admin, workId, "FREE", "판정 근거");
            String note = "OTHER".equals(reason) ? "직접 적은 이유" : null;

            JsonNode result = setRecommended(admin, workId, recommendBody(editionId, reason, note, null));

            assertThat(result.path("editionId").asLong()).isEqualTo(editionId);
            assertThat(recommendation(admin, workId).path("current").path("reason").asText()).isEqualTo(reason);
        }
    }

    // ===== "기타" 면 메모 필수 =====

    @Test
    @DisplayName("'기타' 인데 메모가 비면 400 field note — 사유가 있는 척하는 빈칸을 만들지 않는다 (8-B 4)")
    void otherRequiresNote() throws Exception {
        Tokens admin = loginAdmin();
        long workId = createWork(admin, createComposer(admin));
        long editionId = createFileEdition(admin, workId, "FREE", "판정 근거");

        for (Object emptyNote : new Object[]{null, "", "   "}) {
            adminPut(admin, URL, recommendBody(editionId, "OTHER", (String) emptyNote, null), workId)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.errors[0].field").value("note"));
        }
        assertThat(getWork(admin, workId).path("recommendedEditionId").isNull()).isTrue();
    }

    @Test
    @DisplayName("'기타' 가 아니면 메모를 비워도 저장된다 (8-B 3) — 공백만 적은 메모는 null 로 정규화한다")
    void noteIsOptionalUnlessOther() throws Exception {
        Tokens admin = loginAdmin();
        long composerId = createComposer(admin);

        long blank = createWork(admin, composerId);
        recommendWithNote(admin, blank, null);
        assertThat(recommendation(admin, blank).path("current").path("note").isNull()).isTrue();

        long whitespace = createWork(admin, composerId);
        recommendWithNote(admin, whitespace, "   ");
        assertThat(recommendation(admin, whitespace).path("current").path("note").isNull()).isTrue();
    }

    @Test
    @DisplayName("메모는 300자까지 — 정확히 300자는 통과하고 301자는 400 (§0-6)")
    void noteLengthLimitIs300() throws Exception {
        Tokens admin = loginAdmin();
        long composerId = createComposer(admin);

        long okWork = createWork(admin, composerId);
        long okEdition = createFileEdition(admin, okWork, "FREE", "판정 근거");
        JsonNode saved = setRecommended(admin, okWork, recommendBody(okEdition, "OTHER", "가".repeat(300), null));
        assertThat(saved.path("editionId").asLong()).isEqualTo(okEdition);

        long overWork = createWork(admin, composerId);
        long overEdition = createFileEdition(admin, overWork, "FREE", "판정 근거");
        adminPut(admin, URL, recommendBody(overEdition, "OTHER", "가".repeat(301), null), overWork)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("note"));
        assertThat(getWork(admin, overWork).path("recommendedEditionId").isNull()).isTrue();
    }

    // ===== "미리보기를 확인했어요" 체크 (§3-3) =====

    @Test
    @DisplayName("reviewed: true 면 그 자리에서 검수가 끝난다 — 미검수 목록에 다시 올라오지 않는다")
    void reviewedCheckboxCompletesTheReviewInPlace() throws Exception {
        Tokens admin = loginAdmin();
        long workId = createWork(admin, createComposer(admin));
        long editionId = createFileEdition(admin, workId, "FREE", "판정 근거");

        JsonNode result = setRecommended(admin, workId, recommendBody(editionId, "BETTER_READABILITY", null, true));

        assertThat(result.path("recommendationReviewed").asBoolean()).isTrue();
        assertThat(getWork(admin, workId).path("recommendationReviewed").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("기본값은 꺼짐 — reviewed 를 보내지 않으면 미검수로 남는다 (켜 두면 '항상 0' 인 뜻 없는 표시가 된다)")
    void reviewedDefaultsToFalse() throws Exception {
        Tokens admin = loginAdmin();
        long workId = createWork(admin, createComposer(admin));
        long editionId = createFileEdition(admin, workId, "FREE", "판정 근거");

        JsonNode result = setRecommended(admin, workId, recommendBody(editionId, "BETTER_READABILITY", null, null));

        assertThat(result.path("recommendationReviewed").asBoolean()).isFalse();
        assertThat(getWork(admin, workId).path("recommendationReviewed").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("체크하고 지정한 뒤 다른 판본으로 바꾸면 다시 미검수다 — 확인한 것은 '그 판본' 이 아니라 '지금 추천' 이다")
    void changingTheRecommendationResetsTheReviewAgain() throws Exception {
        Tokens admin = loginAdmin();
        long workId = createWork(admin, createComposer(admin));
        long first = createFileEdition(admin, workId, "FREE", "판정 근거");
        long second = createFileEdition(admin, workId, "FREE", "판정 근거");

        setRecommended(admin, workId, recommendBody(first, "BETTER_READABILITY", null, true));
        JsonNode result = setRecommended(admin, workId, recommendBody(second, "BETTER_READABILITY", null, null));

        assertThat(result.path("recommendationReviewed").asBoolean()).isFalse();
    }

    // ===== 거부의 자리 =====

    @Test
    @DisplayName("없는 곡·다른 곡의 판본은 404 다 — 사유 오류보다 먼저다(있다는 사실을 흘리지 않는다)")
    void notFoundComesBeforeValidation() throws Exception {
        Tokens admin = loginAdmin();
        long composerId = createComposer(admin);
        long workId = createWork(admin, composerId);
        long otherWork = createWork(admin, composerId);
        long otherEdition = createFileEdition(admin, otherWork, "FREE", "판정 근거");

        // 사유도 없고 곡도 없다 → 404
        adminPut(admin, URL, recommendBody(otherEdition, null, null, null), 99_999_999L)
                .andExpect(status().isNotFound());
        // 사유도 없고 남의 판본이다 → 404
        adminPut(admin, URL, recommendBody(otherEdition, null, null, null), workId)
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("파일 없는 판본은 사유를 제대로 골라도 400 으로 막힌다 (8-C 5 — 이 규칙은 그대로다)")
    void missingFileStillBlocksEvenWithAReason() throws Exception {
        Tokens admin = loginAdmin();
        long workId = createWork(admin, createComposer(admin));
        long infoOnly = createInfoEdition(admin, workId);

        adminPut(admin, URL, recommendBody(infoOnly, "COVERS_WHOLE_WORK", null, null), workId)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_RULE_VIOLATION"));
    }

    @Test
    @DisplayName("비로그인 401 · USER 403 — 사유가 생겨도 인가는 그대로다")
    void authIsUnchanged() throws Exception {
        Tokens admin = loginAdmin();
        long workId = createWork(admin, createComposer(admin));
        long editionId = createFileEdition(admin, workId, "FREE", "판정 근거");
        Map<String, Object> request = recommendBody(editionId, "BETTER_READABILITY", null, null);

        mockMvc.perform(put(URL, workId).contentType(MediaType.APPLICATION_JSON).content(body(request)))
                .andExpect(status().isUnauthorized());

        adminPut(loginUser(), URL, request, workId).andExpect(status().isForbidden());
    }

    /** 그 곡에 판본 1개를 만들어 "이 판본이 더 읽기 좋아요" 로 지정한다(메모만 바뀌는 시나리오용). */
    private void recommendWithNote(Tokens admin, long workId, String note) throws Exception {
        long editionId = createFileEdition(admin, workId, "FREE", "판정 근거");
        setRecommended(admin, workId, recommendBody(editionId, "BETTER_READABILITY", note, null));
    }
}
