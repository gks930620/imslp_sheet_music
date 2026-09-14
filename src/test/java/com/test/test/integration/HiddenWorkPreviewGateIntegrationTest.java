package com.test.test.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.test.test.integration.support.AdminApiTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 숨긴 곡의 미리보기 노출 — 02_API_명세서 §0-4 (2026-09-09 신설, senior-dev. qa 5차 결함 3. <b>Red</b>).
 *
 * <h2>계약이 한 조건을 빠뜨렸다</h2>
 * {@code CopyrightEditionPreviewGate.isPreviewOpenToPublic} 의 조건은 {@code koreaCopyright == FREE} 하나뿐이라
 * {@code work.hidden} 을 보지 않는다. 그래서 <b>같은 곡의 곡 상세(§3-3)도 다운로드(§3-4)도 404 인데
 * 미리보기 PNG 만 200</b> 이다.
 *
 * <h2>왜 닫아야 하나</h2>
 * 숨김의 정의가 "사용자 화면 <b>어디에도</b> 안 나온다"(기획 §F2-6)다. 특히 수집이
 * <b>"피아노 독주곡이 아닌 것 같아요"</b> 로 자동 숨김한 곡({@code hidden_reason = NOT_PIANO_SOLO}, §6-11)은
 * <b>우리가 아직 무엇인지 판단하지 못한 곡</b>이다. 판단이 끝나지 않은 것을 공개로 두지 않는다는 점에서
 * "판정이 안 끝난 판본은 미리보기도 감춘다"(§0-4, qa 4차 결함 2)와 같은 결정이다.
 *
 * <p>여기서는 <b>응답을 고칠 것이 없다</b> — 숨긴 곡은 애초에 공개 응답이 없다. 그래서 이 테스트가 다루는 것은
 * 서빙 게이트 한 곳이고, 동시에 "응답에 안 실리니 닫힌 것" 이라는 판단이 통하지 않는 가장 순수한 사례다:
 * 응답이 존재한 적도 없는데 바이트는 열려 있었다(숨기기 전에 공개였던 곡의 주소는 이미 밖에 나가 있다).
 *
 * <h2>관리자는 계속 본다</h2>
 * 숨김을 풀지 말지를 판단하는 근거가 그 1쪽 이미지다(정말 피아노 독주가 아닌가).
 */
class HiddenWorkPreviewGateIntegrationTest extends AdminApiTestSupport {

    @Test
    @DisplayName("숨긴 곡의 FREE 판본 미리보기는 비로그인·USER 에게 404, ADMIN 에게만 200")
    void hiddenWorkPreviewIsClosedToThePublic() throws Exception {
        Tokens admin = loginAdmin();
        Tokens user = loginUser();
        long workId = createWork(admin, createComposer(admin));

        JsonNode upload = uploadSamplePdf(admin);
        createEdition(admin, workId, editionBody(upload.path("fileId").asLong(),
                upload.path("previewFileId").asLong(), upload.path("pageCount").asInt(),
                "FREE", "테스트 판정 근거"));
        String previewUrl = upload.path("previewUrl").asText();

        // 숨기기 전에는 열려 있다 — 그리고 이 주소는 이미 밖으로 나갔다고 봐야 한다
        mockMvc.perform(get(previewUrl)).andExpect(status().isOk());

        setWorkHidden(admin, workId, true);

        mockMvc.perform(get(previewUrl))
                .andExpect(status().isNotFound());
        mockMvc.perform(get(previewUrl).header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
                .andExpect(status().isNotFound());
        mockMvc.perform(get(previewUrl).header(HttpHeaders.AUTHORIZATION, bearer(admin.accessToken())))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("곡 상세·다운로드와 같은 방향이다 — 셋 다 없는 곡처럼 보여야 숨긴 것이다")
    void theWholeWorkIsGoneNotJustPartOfIt() throws Exception {
        Tokens admin = loginAdmin();
        long workId = createWork(admin, createComposer(admin));

        JsonNode upload = uploadSamplePdf(admin);
        long editionId = createEdition(admin, workId, editionBody(upload.path("fileId").asLong(),
                upload.path("previewFileId").asLong(), upload.path("pageCount").asInt(),
                "FREE", "테스트 판정 근거"));
        setRecommended(admin, workId, editionId);
        String previewUrl = upload.path("previewUrl").asText();

        setWorkHidden(admin, workId, true);

        mockMvc.perform(get("/api/works/{id}", workId))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/editions/{id}/download", editionId))
                .andExpect(status().isNotFound());
        mockMvc.perform(get(previewUrl))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("숨김을 풀면 그 즉시 다시 열린다 — 게이트는 저장된 상태를 그대로 따라간다")
    void unhidingOpensItAgain() throws Exception {
        Tokens admin = loginAdmin();
        long workId = createWork(admin, createComposer(admin));

        JsonNode upload = uploadSamplePdf(admin);
        createEdition(admin, workId, editionBody(upload.path("fileId").asLong(),
                upload.path("previewFileId").asLong(), upload.path("pageCount").asInt(),
                "FREE", "테스트 판정 근거"));
        String previewUrl = upload.path("previewUrl").asText();

        setWorkHidden(admin, workId, true);
        mockMvc.perform(get(previewUrl)).andExpect(status().isNotFound());

        setWorkHidden(admin, workId, false);
        mockMvc.perform(get(previewUrl)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("두 조건은 AND 다 — 숨김이면서 판정도 안 끝난 판본도 404, ADMIN 만 200")
    void bothConditionsMustHold() throws Exception {
        Tokens admin = loginAdmin();
        long workId = createWork(admin, createComposer(admin));

        JsonNode upload = uploadSamplePdf(admin);
        createEdition(admin, workId, editionBody(upload.path("fileId").asLong(),
                upload.path("previewFileId").asLong(), upload.path("pageCount").asInt(),
                "UNKNOWN", null));
        String previewUrl = upload.path("previewUrl").asText();

        setWorkHidden(admin, workId, true);

        mockMvc.perform(get(previewUrl)).andExpect(status().isNotFound());
        mockMvc.perform(get(previewUrl).header(HttpHeaders.AUTHORIZATION, bearer(admin.accessToken())))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("숨기지 않은 곡의 FREE 판본은 그대로 200 — 새 조건이 정상 경로를 막지 않는다")
    void visibleWorksAreUnaffected() throws Exception {
        Tokens admin = loginAdmin();
        long workId = createWork(admin, createComposer(admin));

        JsonNode upload = uploadSamplePdf(admin);
        createEdition(admin, workId, editionBody(upload.path("fileId").asLong(),
                upload.path("previewFileId").asLong(), upload.path("pageCount").asInt(),
                "FREE", "테스트 판정 근거"));

        mockMvc.perform(get(upload.path("previewUrl").asText())).andExpect(status().isOk());
    }
}
