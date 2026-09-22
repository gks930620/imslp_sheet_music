package com.test.test.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.test.test.integration.support.AdminApiTestSupport;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 바꾸기 <b>전에</b> 읽는 경고 6종 — 02 §5-6-2 (기획 06 §3-2, 화면정의 06 A-3 ②·②-1).
 *
 * <p>경고 ④⑤는 판본의 성질이 아니라 <b>이 변경의 결과</b>라, 판정에 <b>곡의 지금 상태</b>와
 * <b>그 곡의 다운로드 기록 유무</b>가 필요하다. 그래서 판본 행의 값만 보는 화면은 이것을 셀 수 없고,
 * 서버가 §5-6 과 <b>같은 함수</b>로 계산해 미리 알려 준다(예고와 결과가 어긋나면 안 된다 — 03 §30-3).
 *
 * <p>화면은 <b>한 번에 최대 5줄</b>을 전제로 그린다(②와 ⑥은 {@code kind} 가 하나뿐이라 동시에 성립할 수 없다).
 * 그 상한과 고정 순서를 여기서 잠근다.
 */
class RecommendChangePreviewIntegrationTest extends AdminApiTestSupport {

    private static final String PREVIEW_URL = "/api/admin/works/{workId}/recommended-edition/preview";

    // ===== 아무것도 해당되지 않을 때 =====

    @Test
    @DisplayName("해당되는 것이 없으면 빈 배열 — null 을 내려보내지 않는다")
    void noWarningsIsAnEmptyArray() throws Exception {
        Tokens admin = loginAdmin();
        long workId = createWork(admin, createComposer(admin));
        long target = createFileEdition(admin, workId, "FREE", "판정 근거");

        assertThat(changePreviewWarnings(admin, workId, target)).isEmpty();
    }

    // ===== ④ 이 변경의 결과: 곡이 닫힌다 =====

    @Test
    @DisplayName("바로 받기 가능한 곡을 확인 중 판본으로 바꾸려 하면 ④와 ①이 함께 온다 (8-C 2)")
    void closingAnOpenWorkWarnsTwiceOnPurpose() throws Exception {
        Tokens admin = loginAdmin();
        long workId = createWork(admin, createComposer(admin));
        makeReady(admin, workId);
        long target = createFileEdition(admin, workId, "UNKNOWN", null);

        assertThat(changePreviewWarnings(admin, workId, target))
                .containsExactly("WORK_BECOMES_CLOSED", "NOT_DOWNLOADABLE");
    }

    @Test
    @DisplayName("이미 닫혀 있던 곡에는 ④가 없다 — 닫힌 것을 닫을 수는 없다 (8-C 2)")
    void anAlreadyClosedWorkDoesNotGetTheClosingWarning() throws Exception {
        Tokens admin = loginAdmin();
        long workId = createWork(admin, createComposer(admin));
        long closed = createFileEdition(admin, workId, "UNKNOWN", null);
        setRecommended(admin, workId, closed);
        long target = createFileEdition(admin, workId, "RESTRICTED", "판정 근거");

        assertThat(changePreviewWarnings(admin, workId, target)).containsExactly("NOT_DOWNLOADABLE");
    }

    @Test
    @DisplayName("추천이 아직 없는 곡(첫 지정)에도 ④가 없다 — 열려 있던 적이 없다")
    void aWorkWithoutAnyRecommendationDoesNotGetTheClosingWarning() throws Exception {
        Tokens admin = loginAdmin();
        long workId = createWork(admin, createComposer(admin));
        long target = createFileEdition(admin, workId, "UNKNOWN", null);

        assertThat(changePreviewWarnings(admin, workId, target)).containsExactly("NOT_DOWNLOADABLE");
    }

    @Test
    @DisplayName("열린 곡을 다른 FREE 판본으로 바꾸는 것은 ④가 아니다 — 곡이 닫히지 않는다")
    void swappingBetweenFreeEditionsIsNotAClosing() throws Exception {
        Tokens admin = loginAdmin();
        long workId = createWork(admin, createComposer(admin));
        makeReady(admin, workId);
        long target = createFileEdition(admin, workId, "FREE", "판정 근거");

        assertThat(changePreviewWarnings(admin, workId, target)).isEmpty();
    }

    // ===== ⑤ 이 변경의 결과: 받아 간 사람이 있다 =====

    @Test
    @DisplayName("다운로드 기록이 있는 곡에만 ⑤가 뜬다 — 없는 곡에는 소음을 만들지 않는다 (8-C 3)")
    void downloadHistoryWarningOnlyWhenThereIsHistory() throws Exception {
        Tokens admin = loginAdmin();
        long composerId = createComposer(admin);

        long withHistory = createWork(admin, composerId);
        long recommended = makeReady(admin, withHistory);
        mockMvc.perform(get("/api/editions/{id}/download", recommended)).andExpect(status().isOk());
        long target = createFileEdition(admin, withHistory, "FREE", "판정 근거");
        assertThat(getWork(admin, withHistory).path("hasDownloadHistory").asBoolean()).isTrue();
        assertThat(changePreviewWarnings(admin, withHistory, target)).containsExactly("HAS_DOWNLOAD_HISTORY");

        long withoutHistory = createWork(admin, composerId);
        makeReady(admin, withoutHistory);
        long cleanTarget = createFileEdition(admin, withoutHistory, "FREE", "판정 근거");
        assertThat(changePreviewWarnings(admin, withoutHistory, cleanTarget)).isEmpty();
    }

    // ===== ①②③⑥ 판본의 성질 =====

    @Test
    @DisplayName("파트보는 ⑥ — 편곡(②)과 동시에 성립할 수 없다(종류는 하나다)")
    void partsAndArrangementAreMutuallyExclusive() throws Exception {
        Tokens admin = loginAdmin();
        long composerId = createComposer(admin);

        long partsWork = createWork(admin, composerId);
        long parts = fileEditionWith(admin, partsWork, "FREE", Map.of("kind", "PARTS"));
        assertThat(changePreviewWarnings(admin, partsWork, parts)).containsExactly("PARTS");

        long arrangementWork = createWork(admin, composerId);
        long arrangement = fileEditionWith(admin, arrangementWork, "FREE",
                Map.of("kind", "ARRANGEMENT", "arranger", "Franz Liszt"));
        assertThat(changePreviewWarnings(admin, arrangementWork, arrangement)).containsExactly("ARRANGEMENT");
    }

    @Test
    @DisplayName("특정 악장이면 ③")
    void movementScopeIsPartial() throws Exception {
        Tokens admin = loginAdmin();
        long workId = createWork(admin, createComposer(admin));
        long target = fileEditionWith(admin, workId, "FREE", Map.of("scope", "MOVEMENT", "movementNumber", 2));

        assertThat(changePreviewWarnings(admin, workId, target)).containsExactly("PARTIAL_SCOPE");
    }

    // ===== 겹칠 때 =====

    @Test
    @DisplayName("최악은 5줄이고 순서는 ④ → ⑤ → ① → ⑥/② → ③ 로 고정이다 (화면이 이 상한으로 그린다)")
    void atMostFiveWarningsInAFixedOrder() throws Exception {
        Tokens admin = loginAdmin();
        long workId = createWork(admin, createComposer(admin));
        long recommended = makeReady(admin, workId);
        mockMvc.perform(get("/api/editions/{id}/download", recommended)).andExpect(status().isOk());

        long worst = fileEditionWith(admin, workId, "UNKNOWN", Map.of(
                "kind", "ARRANGEMENT", "arranger", "Franz Liszt", "scope", "MOVEMENT", "movementNumber", 2));

        assertThat(changePreviewWarnings(admin, workId, worst)).containsExactly(
                "WORK_BECOMES_CLOSED", "HAS_DOWNLOAD_HISTORY", "NOT_DOWNLOADABLE", "ARRANGEMENT", "PARTIAL_SCOPE");
    }

    @Test
    @DisplayName("파트보 쪽 최악도 5줄이고 ⑥이 ②의 자리에 온다")
    void thePartsVariantOfTheWorstCase() throws Exception {
        Tokens admin = loginAdmin();
        long workId = createWork(admin, createComposer(admin));
        long recommended = makeReady(admin, workId);
        mockMvc.perform(get("/api/editions/{id}/download", recommended)).andExpect(status().isOk());

        long worst = fileEditionWith(admin, workId, "RESTRICTED", Map.of(
                "kind", "PARTS", "scope", "MOVEMENT", "movementNumber", 3, "copyrightNote", "판정 근거"));

        assertThat(changePreviewWarnings(admin, workId, worst)).containsExactly(
                "WORK_BECOMES_CLOSED", "HAS_DOWNLOAD_HISTORY", "NOT_DOWNLOADABLE", "PARTS", "PARTIAL_SCOPE");
    }

    // ===== 예고와 결과가 같아야 한다 =====

    @Test
    @DisplayName("§5-6 응답의 warnings 는 지정 '직전' 상태로 계산된다 — 지정한 뒤에 세면 ④가 영영 안 뜬다")
    void assignmentResponseCarriesTheSameWarningsAsThePreview() throws Exception {
        Tokens admin = loginAdmin();
        long workId = createWork(admin, createComposer(admin));
        long recommended = makeReady(admin, workId);
        mockMvc.perform(get("/api/editions/{id}/download", recommended)).andExpect(status().isOk());
        long target = fileEditionWith(admin, workId, "UNKNOWN", Map.of("scope", "MOVEMENT", "movementNumber", 2));

        var predicted = changePreviewWarnings(admin, workId, target);
        JsonNode result = setRecommended(admin, workId,
                recommendBody(target, "COVERS_WHOLE_WORK", null, null));

        assertThat(strings(result.path("warnings"))).isEqualTo(predicted);
        assertThat(strings(result.path("warnings")))
                .containsExactly("WORK_BECOMES_CLOSED", "HAS_DOWNLOAD_HISTORY", "NOT_DOWNLOADABLE", "PARTIAL_SCOPE");
        // 경고는 막지 않는다 — 지정은 끝나 있다 (8-C 5)
        assertThat(getWork(admin, workId).path("recommendedEditionId").asLong()).isEqualTo(target);
    }

    // ===== 에러 경로 =====

    @Test
    @DisplayName("파일이 없는 판본도 예고는 200 — 읽기를 게이트로 쓰지 않는다(막는 것은 §5-6 의 일)")
    void previewDoesNotBlockFilelessEditions() throws Exception {
        Tokens admin = loginAdmin();
        long workId = createWork(admin, createComposer(admin));
        long infoOnly = createInfoEdition(admin, workId);

        assertThat(changePreviewWarnings(admin, workId, infoOnly)).containsExactly("NOT_DOWNLOADABLE");
    }

    @Test
    @DisplayName("editionId 가 없으면 400, 없는 곡·남의 판본이면 404")
    void missingOrForeignIdentifiers() throws Exception {
        Tokens admin = loginAdmin();
        long composerId = createComposer(admin);
        long workId = createWork(admin, composerId);
        long otherWork = createWork(admin, composerId);
        long otherEdition = createFileEdition(admin, otherWork, "FREE", "판정 근거");

        adminGet(admin, PREVIEW_URL, workId).andExpect(status().isBadRequest());
        adminQuery(admin, "/api/admin/works/99999999/recommended-edition/preview",
                "editionId", String.valueOf(otherEdition)).andExpect(status().isNotFound());
        adminQuery(admin, "/api/admin/works/" + workId + "/recommended-edition/preview",
                "editionId", String.valueOf(otherEdition)).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("비로그인 401 · USER 403")
    void previewIsAdminOnly() throws Exception {
        Tokens admin = loginAdmin();
        long workId = createWork(admin, createComposer(admin));
        long target = createFileEdition(admin, workId, "FREE", "판정 근거");

        mockMvc.perform(get(PREVIEW_URL, workId).param("editionId", String.valueOf(target)))
                .andExpect(status().isUnauthorized());
        adminQuery(loginUser(), "/api/admin/works/" + workId + "/recommended-edition/preview",
                "editionId", String.valueOf(target)).andExpect(status().isForbidden());
    }

    /** 샘플 PDF 를 붙인 판본 1개. {@code extra} 로 kind/scope 등을 덮어쓴다. */
    private long fileEditionWith(Tokens admin, long workId, String koreaCopyright, Map<String, Object> extra)
            throws Exception {
        JsonNode upload = uploadSamplePdf(admin);
        Map<String, Object> body = editionBody(upload.path("fileId").asLong(), upload.path("previewFileId").asLong(),
                upload.path("pageCount").asInt(), koreaCopyright, "UNKNOWN".equals(koreaCopyright) ? null : "판정 근거");
        body.putAll(extra);
        return createEdition(admin, workId, body);
    }
}
