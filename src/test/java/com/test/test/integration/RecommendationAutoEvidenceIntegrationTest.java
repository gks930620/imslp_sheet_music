package com.test.test.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.test.test.integration.support.AdminApiTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 자동으로 골랐을 때의 근거 — 02 §5-11 · §4-7-2 (기획 06 §1-3, 인수 조건 8-A 2·3 · 8-D 3·4 · 8-F 5).
 *
 * <p><b>이 파일이 지키는 한 문장</b>: 자동 근거는 <b>지정 시점 값</b>이다. 나중에 IMSLP 다운로드 수가 바뀌거나
 * 판본이 늘어도 근거는 바뀌지 않는다 — 다시 읽어 계산하면 그건 그때의 판단을 설명하는 것이 아니라
 * <b>오늘 다시 낸 답</b>이고, 그건 근거가 아니다(기획 §1-3).
 *
 * <p>화면이 "후보 1개" 와 "다운로드 수 없음" 을 <b>별도 분기</b>로 그리므로 둘은 각각 구분 가능해야 한다
 * ({@code candidateCount == 1} / {@code imslpDownloadCount == null}).
 */
class RecommendationAutoEvidenceIntegrationTest extends AdminApiTestSupport {

    private static final String AUTO_JUDGE_URL = "/api/admin/copyright/auto-judge";
    private static final String UNDO_URL = "/api/admin/copyright/auto-judge/undo";

    @Test
    @DisplayName("자동 지정은 규칙·그때의 다운로드 수·후보 수·순위를 남긴다 (8-A 2 · 8-D 3 · 8-F 5)")
    void autoAssignmentRecordsTheRuleAndTheNumbersOfThatMoment() throws Exception {
        Tokens admin = loginAdmin();
        long workId = createWork(admin, createComposer(admin));
        long best = createFileEdition(admin, workId, "FREE", "판정 근거");
        long second = createFileEdition(admin, workId, "FREE", "판정 근거");
        long third = createFileEdition(admin, workId, "FREE", "판정 근거");
        setImslpDownloadCount(best, 1204);
        setImslpDownloadCount(second, 300);
        setImslpDownloadCount(third, 12);

        autoJudge(admin);

        assertThat(getWork(admin, workId).path("recommendedEditionId").asLong()).isEqualTo(best);
        JsonNode current = recommendation(admin, workId).path("current");
        assertThat(current.path("source").asText()).isEqualTo("AUTO");
        assertThat(current.path("action").asText()).isEqualTo("ASSIGNED");
        // 자동은 사람이 아니다 — 이름도 사유도 메모도 없다
        assertThat(current.path("decidedByNickname").isNull()).isTrue();
        assertThat(current.path("reason").isNull()).isTrue();
        assertThat(current.path("note").isNull()).isTrue();
        // 자동 지정은 추천이 없는 곡에만 걸린다 → 언제나 첫 지정
        assertThat(current.path("previousEdition").isNull()).isTrue();

        JsonNode auto = current.path("auto");
        assertThat(auto.path("rule").asText()).isEqualTo("MOST_IMSLP_DOWNLOADS");
        assertThat(auto.path("imslpDownloadCount").asInt()).isEqualTo(1204);
        assertThat(auto.path("candidateCount").asInt()).isEqualTo(3);
        assertThat(auto.path("rank").asInt()).isEqualTo(1);
    }

    @Test
    @DisplayName("나중에 다운로드 수가 바뀌어도 근거는 그대로다 — 근거는 '그때의 판단' 이다 (기획 §1-3)")
    void evidenceIsFrozenAtAssignmentTime() throws Exception {
        Tokens admin = loginAdmin();
        long workId = createWork(admin, createComposer(admin));
        long chosen = createFileEdition(admin, workId, "FREE", "판정 근거");
        long other = createFileEdition(admin, workId, "FREE", "판정 근거");
        setImslpDownloadCount(chosen, 1204);
        setImslpDownloadCount(other, 300);
        autoJudge(admin);

        // 수집이 다시 돌아 수가 바뀌고, 판본이 하나 더 생겼다
        setImslpDownloadCount(chosen, 99_999);
        setImslpDownloadCount(other, 88_888);
        createFileEdition(admin, workId, "FREE", "판정 근거");

        JsonNode auto = recommendation(admin, workId).path("current").path("auto");
        assertThat(auto.path("imslpDownloadCount").asInt()).isEqualTo(1204);
        assertThat(auto.path("candidateCount").asInt()).isEqualTo(2);
    }

    @Test
    @DisplayName("후보가 1개뿐이었으면 candidateCount 가 1 이다 — 화면이 '1위' 라고 쓰지 않는 근거 (8-A 3)")
    void aSingleCandidateIsTellableFromTheNumber() throws Exception {
        Tokens admin = loginAdmin();
        long workId = createWork(admin, createComposer(admin));
        long only = createFileEdition(admin, workId, "FREE", "판정 근거");
        setImslpDownloadCount(only, 1204);

        autoJudge(admin);

        JsonNode auto = recommendation(admin, workId).path("current").path("auto");
        assertThat(auto.path("candidateCount").asInt()).isEqualTo(1);
        assertThat(auto.path("rank").asInt()).isEqualTo(1);
        assertThat(auto.path("imslpDownloadCount").asInt()).isEqualTo(1204);
    }

    @Test
    @DisplayName("다운로드 수가 없던 판본은 imslpDownloadCount 가 null 이다 — 0 으로 뭉개지 않는다")
    void missingDownloadCountIsNullNotZero() throws Exception {
        Tokens admin = loginAdmin();
        long workId = createWork(admin, createComposer(admin));
        long a = createFileEdition(admin, workId, "FREE", "판정 근거");
        createFileEdition(admin, workId, "FREE", "판정 근거");
        // 둘 다 수가 없다 → 규칙상 id 오름차순으로 앞의 것이 뽑힌다
        setImslpDownloadCount(a, null);

        autoJudge(admin);

        JsonNode auto = recommendation(admin, workId).path("current").path("auto");
        assertThat(auto.path("imslpDownloadCount").isNull()).isTrue();
        assertThat(auto.path("candidateCount").asInt()).isEqualTo(2);   // "후보 1개" 와는 다른 분기다
    }

    @Test
    @DisplayName("dryRun 은 근거도 만들지 않는다 — 미리보기가 기록을 남기면 그건 미리보기가 아니다")
    void dryRunLeavesNoRecord() throws Exception {
        Tokens admin = loginAdmin();
        long workId = createWork(admin, createComposer(admin));
        createFileEdition(admin, workId, "FREE", "판정 근거");

        adminPost(admin, AUTO_JUDGE_URL, json("dryRun", true, "assignRecommended", true))
                .andExpect(status().isOk());

        assertThat(getWork(admin, workId).path("recommendedEditionId").isNull()).isTrue();
        assertThat(recommendation(admin, workId).path("historyCount").asInt()).isZero();
    }

    @Test
    @DisplayName("이미 추천이 있는 곡에는 자동 근거가 생기지 않는다 — 실데이터 42곡이 그 상태다 (8-E 1)")
    void worksThatAlreadyHaveARecommendationAreNotTouched() throws Exception {
        Tokens admin = loginAdmin();
        long workId = createWork(admin, createComposer(admin));
        long editionId = makeReady(admin, workId);
        createFileEdition(admin, workId, "FREE", "판정 근거");
        clearRecommendationLog(workId);

        autoJudge(admin);

        assertThat(getWork(admin, workId).path("recommendedEditionId").asLong()).isEqualTo(editionId);
        JsonNode recommendation = recommendation(admin, workId);
        assertThat(recommendation.path("current").isNull()).isTrue();
        assertThat(recommendation.path("historyCount").asInt()).isZero();
    }

    // ===== §5-12 자동 판정만 되돌리기 (8-D 4) =====

    @Test
    @DisplayName("'자동 판정만 되돌리기' 뒤에도 근거·이력이 한 글자도 바뀌지 않는다 (8-D 4 — 바뀌면 결함)")
    void undoingTheAutoJudgementDoesNotTouchTheEvidence() throws Exception {
        Tokens admin = loginAdmin();
        long workId = createWork(admin, createComposer(admin));
        long editionId = createFileEdition(admin, workId, "UNKNOWN", null);
        setImslpDownloadCount(editionId, 1204);
        // 자동 판정이 FREE 로 열고 추천까지 지정하게 한다(판정 근거는 02 부록 A — 편집자 표기 없는 PD 스캔)
        makeAutoJudgeable(admin, editionId);
        autoJudge(admin);
        JsonNode before = recommendation(admin, workId);
        assertThat(before.path("current").path("source").asText()).isEqualTo("AUTO");

        adminPost(admin, UNDO_URL, json()).andExpect(status().isOk());

        JsonNode after = recommendation(admin, workId);
        // 추천은 유지되고(§5-12), 근거도 그대로다
        assertThat(getWork(admin, workId).path("recommendedEditionId").asLong()).isEqualTo(editionId);
        assertThat(after).isEqualTo(before);
    }

    // ===== 도우미 =====

    private void autoJudge(Tokens admin) throws Exception {
        adminPost(admin, AUTO_JUDGE_URL, json("dryRun", false, "assignRecommended", true))
                .andExpect(status().isOk());
    }

    /**
     * 자동 판정(02 §5-11, 기획 02 부록 A §A-1 규칙 5)이 FREE 로 열 수 있는 판본으로 만든다 —
     * Public Domain 표기 + 편집자·편곡자 표기 없음. 작곡가 몰년은 {@code composerBody} 가 1850 으로 넣는다.
     */
    private void makeAutoJudgeable(Tokens admin, long editionId) throws Exception {
        java.util.Map<String, Object> body = editionSaveBodyFrom(getEdition(admin, editionId));
        body.put("imslpCopyrightText", "Public Domain");
        body.put("editor", null);
        body.put("arranger", null);
        adminPut(admin, "/api/admin/editions/{id}", body, editionId).andExpect(status().isOk());
    }
}
