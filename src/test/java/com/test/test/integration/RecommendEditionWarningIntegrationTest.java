package com.test.test.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.test.test.integration.support.AdminApiTestSupport;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PUT /api/admin/works/{workId}/recommended-edition 의 경고 — 02 §5-6 (기획 01 §11-2 ①, §3 F6-3).
 *
 * <p>경고는 하나가 아니라 <b>해당되는 것이 전부</b> 온다. 겹칠 때 하나만 보이면 관리자는 나머지를 모른 채 지정한다.
 * 경고는 막지 않는다 — 지정은 200 으로 끝나 있고, 그 선택의 결과는 사용자 화면(검색 한 줄·곡 상세·파일명)으로 드러난다.
 */
class RecommendEditionWarningIntegrationTest extends AdminApiTestSupport {

    @Test
    @DisplayName("전체 악보 · 전곡 · FREE 면 warnings 는 빈 배열 (null 이 아니다)")
    void noWarnings_isAnEmptyArray() throws Exception {
        Tokens admin = loginAdmin();
        long workId = createWork(admin, createComposer(admin));
        long editionId = createFileEdition(admin, workId, "FREE", "판정 근거");

        JsonNode result = setRecommended(admin, workId, editionId);
        assertThat(result.path("workStatus").asText()).isEqualTo("READY");
        assertThat(result.path("warnings").isArray()).isTrue();
        assertThat(strings(result.path("warnings"))).isEmpty();
        // 옛 단수 필드는 계약에서 사라졌다 — 같은 뜻의 필드를 둘 두지 않는다
        assertThat(result.has("warning")).isFalse();
    }

    @Test
    @DisplayName("판정이 FREE 가 아니면 NOT_DOWNLOADABLE")
    void notDownloadable_whenJudgementIsNotFree() throws Exception {
        Tokens admin = loginAdmin();
        long composerId = createComposer(admin);

        long unknownWork = createWork(admin, composerId);
        JsonNode unknown = setRecommended(admin, unknownWork, createFileEdition(admin, unknownWork, "UNKNOWN", null));
        assertThat(strings(unknown.path("warnings"))).containsExactly("NOT_DOWNLOADABLE");
        assertThat(unknown.path("workStatus").asText()).isEqualTo("UNKNOWN");

        long restrictedWork = createWork(admin, composerId);
        JsonNode restricted = setRecommended(admin, restrictedWork,
                createFileEdition(admin, restrictedWork, "RESTRICTED", "판정 근거"));
        assertThat(strings(restricted.path("warnings"))).containsExactly("NOT_DOWNLOADABLE");
    }

    @Test
    @DisplayName("편곡이면 ARRANGEMENT, 특정 악장이면 PARTIAL_SCOPE — 지정 자체는 막지 않는다")
    void arrangementAndPartialScope_areWarnedButAllowed() throws Exception {
        Tokens admin = loginAdmin();
        long composerId = createComposer(admin);

        long arrangementWork = createWork(admin, composerId);
        JsonNode arrangement = setRecommended(admin, arrangementWork,
                fileEdition(admin, arrangementWork, "FREE", Map.of("kind", "ARRANGEMENT", "arranger", "Franz Liszt")));
        assertThat(strings(arrangement.path("warnings"))).containsExactly("ARRANGEMENT");
        // 경고가 떠도 곡은 열린다 (기획 §11-2-4)
        assertThat(arrangement.path("workStatus").asText()).isEqualTo("READY");

        long movementWork = createWork(admin, composerId);
        JsonNode movement = setRecommended(admin, movementWork,
                fileEdition(admin, movementWork, "FREE", Map.of("scope", "MOVEMENT", "movementNumber", 2)));
        assertThat(strings(movement.path("warnings"))).containsExactly("PARTIAL_SCOPE");
        assertThat(movement.path("workStatus").asText()).isEqualTo("READY");
    }

    @Test
    @DisplayName("겹치면 전부 보인다 — 고정 순서 NOT_DOWNLOADABLE → ARRANGEMENT → PARTIAL_SCOPE")
    void allApplicableWarnings_comeBackInFixedOrder() throws Exception {
        Tokens admin = loginAdmin();
        long workId = createWork(admin, createComposer(admin));
        long editionId = fileEdition(admin, workId, "UNKNOWN",
                Map.of("kind", "ARRANGEMENT", "scope", "MOVEMENT", "movementNumber", 2));

        JsonNode result = setRecommended(admin, workId, editionId);
        assertThat(strings(result.path("warnings")))
                .containsExactly("NOT_DOWNLOADABLE", "ARRANGEMENT", "PARTIAL_SCOPE");
        assertThat(result.path("editionId").asLong()).isEqualTo(editionId);
        assertThat(result.path("workStatus").asText()).isEqualTo("UNKNOWN");

        // 지정은 실제로 저장됐다 — 경고는 안내이지 거부가 아니다
        assertThat(getWork(admin, workId).path("recommendedEditionId").asLong()).isEqualTo(editionId);
    }

    @Test
    @DisplayName("파일이 없는 판본만 400 으로 막는다 (경고와 거부는 다른 자리다)")
    void onlyMissingFileIsRejected() throws Exception {
        Tokens admin = loginAdmin();
        long workId = createWork(admin, createComposer(admin));
        long infoOnly = createInfoEdition(admin, workId);

        adminPut(admin, "/api/admin/works/{workId}/recommended-edition", json("editionId", infoOnly), workId)
                .andExpect(status().isBadRequest());
    }

    /** 샘플 PDF 를 붙인 판본 1개. {@code extra} 로 kind/scope 등을 덮어쓴다. */
    private long fileEdition(Tokens admin, long workId, String koreaCopyright, Map<String, Object> extra)
            throws Exception {
        JsonNode upload = uploadSamplePdf(admin);
        Map<String, Object> body = editionBody(upload.path("fileId").asLong(), upload.path("previewFileId").asLong(),
                upload.path("pageCount").asInt(), koreaCopyright, "UNKNOWN".equals(koreaCopyright) ? null : "판정 근거");
        body.putAll(extra);
        return createEdition(admin, workId, body);
    }
}
