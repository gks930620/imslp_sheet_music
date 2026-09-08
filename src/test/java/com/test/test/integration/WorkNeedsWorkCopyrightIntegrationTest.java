package com.test.test.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.test.test.integration.support.AdminApiTestSupport;
import java.time.Year;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 보완 필요에 "추천 판본의 저작권 판정이 확인 중" 을 더한다 — 기획 01 §11-1, 01_ERD §4, 02 §4-1·§4-6·§4-7·§5-12.
 *
 * <p>관리자의 일감은 곡 단위다. 추천 판본이 있어도 판정이 UNKNOWN 이면 그 곡은 사용자에게 <b>닫혀 있고</b>,
 * 목록·카운트·곡 편집 세 곳이 같은 규칙으로 그 곡을 보여줘야 한다. RESTRICTED 는 사람이 내린 결론이라 세지 않는다.
 *
 * <p>대시보드 숫자는 전역 집계라 절대값이 아니라 <b>실행 전후 차이</b>로 검증한다(테스트 트랜잭션은 롤백된다).
 */
class WorkNeedsWorkCopyrightIntegrationTest extends AdminApiTestSupport {

    private static final String AUTO_JUDGE_URL = "/api/admin/copyright/auto-judge";
    private static final String UNDO_URL = "/api/admin/copyright/auto-judge/undo";
    private static final int THIS_YEAR = Year.now().getValue();

    @Test
    @DisplayName("추천 판본 판정이 UNKNOWN 이면 missing = [COPYRIGHT_JUDGMENT], needsWork = true")
    void missing_includesCopyrightJudgment_whenRecommendedEditionIsUnknown() throws Exception {
        Tokens admin = loginAdmin();
        long workId = createWork(admin, createComposer(admin));
        long editionId = createFileEdition(admin, workId, "UNKNOWN", null);
        setRecommended(admin, workId, editionId);

        JsonNode work = getWork(admin, workId);
        assertThat(work.path("status").asText()).isEqualTo("UNKNOWN");
        assertThat(work.path("recommendedEditionId").asLong()).isEqualTo(editionId);
        assertThat(work.path("needsWork").asBoolean()).isTrue();
        assertThat(strings(work.path("missing"))).containsExactly("COPYRIGHT_JUDGMENT");
    }

    @Test
    @DisplayName("RESTRICTED 는 사람이 내린 결론이라 세지 않는다 / FREE 도 물론 아니다")
    void restrictedIsAFinishedDecision_andIsNotCounted() throws Exception {
        Tokens admin = loginAdmin();
        long composerId = createComposer(admin);

        long restricted = createWork(admin, composerId);
        setRecommended(admin, restricted, createFileEdition(admin, restricted, "RESTRICTED", "편집자 사후 70년 미경과"));
        JsonNode restrictedWork = getWork(admin, restricted);
        assertThat(restrictedWork.path("status").asText()).isEqualTo("RESTRICTED");
        assertThat(restrictedWork.path("needsWork").asBoolean()).isFalse();
        assertThat(restrictedWork.path("missing")).isEmpty();

        long ready = createWork(admin, composerId);
        makeReady(admin, ready);
        JsonNode readyWork = getWork(admin, ready);
        assertThat(readyWork.path("needsWork").asBoolean()).isFalse();
        assertThat(readyWork.path("missing")).isEmpty();
    }

    @Test
    @DisplayName("추천이 아예 없으면 RECOMMENDED_EDITION 하나만 — 두 값이 함께 나오지 않는다")
    void recommendedEditionAndCopyrightJudgment_neverAppearTogether() throws Exception {
        Tokens admin = loginAdmin();
        long workId = createWork(admin, createComposer(admin));
        createFileEdition(admin, workId, "UNKNOWN", null);   // 판본은 있으나 추천이 아니다

        JsonNode work = getWork(admin, workId);
        assertThat(work.path("status").asText()).isEqualTo("PREPARING");
        assertThat(strings(work.path("missing"))).containsExactly("RECOMMENDED_EDITION");
    }

    @Test
    @DisplayName("목록 필터(NEEDS_WORK) · 관리 홈 카운트 · 곡 편집 표시가 한 규칙을 공유한다")
    void listFilter_dashboardCount_andWorkDetail_shareOneRule() throws Exception {
        Tokens admin = loginAdmin();
        long composerId = createComposer(admin);
        JsonNode before = dashboard(admin);

        long unknownWork = createWork(admin, composerId);
        setRecommended(admin, unknownWork, createFileEdition(admin, unknownWork, "UNKNOWN", null));

        long restrictedWork = createWork(admin, composerId);
        setRecommended(admin, restrictedWork, createFileEdition(admin, restrictedWork, "RESTRICTED", "판정 근거"));

        // (1) 카운트: 확인 중 곡만 1 늘어난다
        JsonNode after = dashboard(admin);
        assertThat(delta(before, after, "needsWorkWorks")).isEqualTo(1);

        // (2) 목록: 같은 곡이 NEEDS_WORK 필터에 걸린다
        JsonNode needsWork = data(adminQuery(admin, "/api/admin/works",
                "composerId", String.valueOf(composerId), "status", "NEEDS_WORK").andExpect(status().isOk()));
        assertThat(longs(needsWork.path("works").path("content"), "id"))
                .contains(unknownWork)
                .doesNotContain(restrictedWork);

        // (3) 곡 편집: 같은 곡에 같은 이유가 적힌다
        assertThat(strings(getWork(admin, unknownWork).path("missing"))).containsExactly("COPYRIGHT_JUDGMENT");
        assertThat(getWork(admin, restrictedWork).path("missing")).isEmpty();

        // UNKNOWN 은 WorkStatus 이자 NEEDS_WORK 대상이다 — 두 목록에 같이 나오는 것이 정상(02 §4-6)
        JsonNode unknownStatus = data(adminQuery(admin, "/api/admin/works",
                "composerId", String.valueOf(composerId), "status", "UNKNOWN").andExpect(status().isOk()));
        assertThat(longs(unknownStatus.path("works").path("content"), "id")).contains(unknownWork);
    }

    @Test
    @DisplayName("자동 판정 되돌리기 후 곡이 일감으로 복귀하고 대시보드 숫자도 실행 전으로 돌아온다")
    void undo_bringsTheWorkBackToTheAdminTodoList() throws Exception {
        Tokens admin = loginAdmin();
        long workId = autoJudgeableWork(admin);
        JsonNode before = dashboard(admin);

        // 자동 판정 → 판정 FREE + 추천 자동 지정 → 곡이 열리고 일감에서 빠진다
        JsonNode judged = data(adminPost(admin, AUTO_JUDGE_URL, json()).andExpect(status().isOk()));
        assertThat(judged.path("judgedFree").asInt()).isEqualTo(1);
        assertThat(judged.path("recommendedAssigned").asInt()).isEqualTo(1);

        JsonNode opened = getWork(admin, workId);
        assertThat(opened.path("status").asText()).isEqualTo("READY");
        assertThat(opened.path("needsWork").asBoolean()).isFalse();
        long recommendedEditionId = opened.path("recommendedEditionId").asLong();

        JsonNode afterJudge = dashboard(admin);
        assertThat(delta(before, afterJudge, "readyWorks")).isEqualTo(1);
        assertThat(delta(before, afterJudge, "needsWorkWorks")).isEqualTo(-1);

        // 되돌리기 — 판정만 되돌리고 추천은 유지한다. 그 사실을 숫자로 말한다(02 §5-12)
        JsonNode undone = data(adminPost(admin, UNDO_URL, json()).andExpect(status().isOk()));
        assertThat(undone.path("reverted").asInt()).isEqualTo(1);
        assertThat(undone.path("recommendationKept").asInt()).isEqualTo(1);

        // 곡은 다시 관리자 일감으로 — 추천은 그대로 남아 있다
        JsonNode reverted = getWork(admin, workId);
        assertThat(reverted.path("recommendedEditionId").asLong()).isEqualTo(recommendedEditionId);
        assertThat(reverted.path("status").asText()).isEqualTo("UNKNOWN");
        assertThat(reverted.path("needsWork").asBoolean()).isTrue();
        assertThat(strings(reverted.path("missing"))).containsExactly("COPYRIGHT_JUDGMENT");

        JsonNode afterUndo = dashboard(admin);
        assertThat(delta(before, afterUndo, "needsWorkWorks")).isZero();
        assertThat(delta(before, afterUndo, "readyWorks")).isZero();
        assertThat(delta(before, afterUndo, "unknownCopyrightEditions")).isZero();
    }

    // ===== 헬퍼 =====

    /** 자동 판정(PD_NO_EDITOR)으로 열릴 수 있는 판본 1개를 가진 곡 — 추천은 아직 없다. */
    private long autoJudgeableWork(Tokens admin) throws Exception {
        String id = uniq();
        Map<String, Object> composer = composerBody("테스트오래전작곡가" + id, "Testlongdeadneeds, " + id);
        composer.put("birthYear", THIS_YEAR - 250);
        composer.put("deathYear", THIS_YEAR - 200);
        long composerId = createComposer(admin, composer);

        long workId = createWork(admin, composerId);
        JsonNode upload = uploadSamplePdf(admin);
        Map<String, Object> edition = editionBody(upload.path("fileId").asLong(),
                upload.path("previewFileId").asLong(), upload.path("pageCount").asInt(), "UNKNOWN", null);
        edition.put("imslpCopyrightText", "Public Domain");
        createEdition(admin, workId, edition);
        return workId;
    }

    private JsonNode dashboard(Tokens admin) throws Exception {
        return data(adminGet(admin, "/api/admin/dashboard").andExpect(status().isOk()));
    }

    private int delta(JsonNode before, JsonNode after, String field) {
        return after.path(field).asInt() - before.path(field).asInt();
    }
}
