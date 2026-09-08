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
 * 못 주는 곡이 내보내는 IMSLP 링크 — 02_API_명세서 §3-3, 기획 §F3-7 · §10-5
 * (2026-09-08 계약 보완, senior-dev. qa 4차 결함 9. <b>Red</b>).
 *
 * <h2>화면이 만들 수 없는 링크를 화면에 요구하고 있었다</h2>
 * 기획 §10-5 는 준비 중·이용 제한·확인 중인 곡의 대안 링크를 IMSLP <b>작품 페이지</b>에서
 * <b>우리가 고른 판본의 파일 페이지</b>로 바꿨다 — 작품 페이지로 보내면 사용자를 "판본 70개 중 고르기" 앞에
 * 그대로 내려놓기 때문이다. 그런데 준비 중 곡은 추천 판본이 없고, 공개 응답의 {@code otherEditions} 는
 * <b>파일 있는 판본만</b> 담으므로(§3-3, 2026-09-08) 판본이 하나도 실리지 않는다.
 * 화면에는 {@code imslpUrl}(작품 페이지)밖에 없어서 §10-5 이전으로 되돌아가 있었다(qa 4차 실측).
 *
 * <h2>확정 계약: {@code imslpCandidateEdition}</h2>
 * <ul>
 *   <li><b>언제 채우나</b> — {@code recommendedEdition == null} 일 때만. 추천이 있으면 화면은 추천 카드의
 *       {@code imslpFileUrl} 을 쓴다(이용 제한·확인 중이어도 추천 카드는 그대로 있다). 두 자리에서 같은 링크를
 *       만들 수 있으면 어느 쪽을 쓸지 화면마다 갈린다.</li>
 *   <li><b>무엇을 고르나</b> — {@code kind = COMPLETE_SCORE} 이고 {@code scope = COMPLETE} 인 판본 중
 *       IMSLP 다운로드 수 DESC NULLS LAST, id ASC 첫 번째. <b>파일 유무는 보지 않는다</b>
 *       (01 §3-3 의 추천 후보 규칙과 같되 파일 조건만 뺀 것이다 — 준비 중 곡에는 파일이 없기 때문).</li>
 *   <li><b>없으면 null</b> — 전체 악보·전곡 판본이 하나도 없으면(편곡뿐이거나 판본 0개) null 이고,
 *       화면은 그때만 {@code imslpUrl}(작품 페이지)로 폴백한다. 기획 §F3-7 의 "그 판본조차 없으면 작품 페이지" 다.
 *       편곡을 "우리가 고른 판본" 이라며 내보내지 않는다 — 1차 범위는 피아노 독주다(기획 §0-2).</li>
 *   <li><b>타입은 {@code EditionDTO}</b> — 화면 문구가 "IMSLP 에서 이 판본 보기 ↗ — 전체 악보 · 전곡 · 12쪽 · Breitkopf 1862"
 *       라서 kind·scope·pageCount·publisher 가 다 필요하다. 파일이 없으므로 {@code hasFile=false},
 *       {@code downloadable=false}, {@code downloadUrl=null} 이다 — <b>이걸로 다운로드 버튼을 만들면 안 된다.</b></li>
 * </ul>
 */
class WorkDetailImslpCandidateIntegrationTest extends AdminApiTestSupport {

    private static final String FILE_PAGE = "https://imslp.org/wiki/Special:ImagefromIndex/";

    @Test
    @DisplayName("준비 중 곡: 전체 악보·전곡 판본의 파일 페이지를 내려준다 — 편곡은 고르지 않는다")
    void preparingWorkExposesTheCandidateFilePage() throws Exception {
        Tokens admin = loginAdmin();
        long workId = createWork(admin, createComposer(admin));

        long arrangement = createFilelessEdition(admin, workId, "ARRANGEMENT", "COMPLETE", FILE_PAGE + "arr");
        long candidate = createFilelessEdition(admin, workId, "COMPLETE_SCORE", "COMPLETE", FILE_PAGE + "cand");
        // 같은 자격의 판본이 하나 더 있어도 id 가 작은 쪽이 뽑힌다(동률 규칙).
        createFilelessEdition(admin, workId, "COMPLETE_SCORE", "COMPLETE", FILE_PAGE + "later");

        JsonNode data = data(mockMvc.perform(get("/api/works/{id}", workId)).andExpect(status().isOk()));

        assertThat(data.path("recommendedEdition").isNull()).isTrue();
        JsonNode picked = data.path("imslpCandidateEdition");
        assertThat(picked.path("id").asLong())
                .as("전체 악보·전곡 중 첫 번째(IMSLP 다운로드 수 동률이면 id ASC) — 편곡 %d 는 고르지 않는다", arrangement)
                .isEqualTo(candidate);
        assertThat(picked.path("imslpFileUrl").asText())
                .as("사용자를 판본 목록이 아니라 이 파일 페이지로 바로 보낸다 (기획 §10-5)")
                .isEqualTo(FILE_PAGE + "cand");
        assertThat(picked.path("hasFile").asBoolean()).isFalse();
        assertThat(picked.path("downloadable").asBoolean()).isFalse();
        assertThat(picked.path("downloadUrl").isNull())
                .as("파일이 없는 판본이다 — 이걸로 다운로드 버튼을 만들면 안 된다")
                .isTrue();
        assertThat(picked.path("kind").asText()).isEqualTo("COMPLETE_SCORE");
        assertThat(picked.path("scope").asText()).isEqualTo("COMPLETE");
    }

    @Test
    @DisplayName("추천 판본이 있으면 null — 링크를 만들 자리는 추천 카드 하나뿐이다")
    void recommendedWorkHasNoCandidate() throws Exception {
        Tokens admin = loginAdmin();
        long workId = createWork(admin, createComposer(admin));
        long recommended = createFileEdition(admin, workId, "UNKNOWN", null);
        setRecommended(admin, workId, recommended);
        createFilelessEdition(admin, workId, "COMPLETE_SCORE", "COMPLETE", FILE_PAGE + "other");

        JsonNode data = data(mockMvc.perform(get("/api/works/{id}", workId)).andExpect(status().isOk()));

        assertThat(data.path("recommendedEdition").path("id").asLong()).isEqualTo(recommended);
        assertThat(data.path("imslpCandidateEdition").isNull())
                .as("확인 중이어도 추천 카드가 있으면 그 카드의 imslpFileUrl 을 쓴다")
                .isTrue();
    }

    @Test
    @DisplayName("전체 악보·전곡 판본이 없으면 null — 그때만 화면이 작품 페이지로 폴백한다")
    void noCompleteScoreMeansNull() throws Exception {
        Tokens admin = loginAdmin();
        long workId = createWork(admin, createComposer(admin));
        createFilelessEdition(admin, workId, "ARRANGEMENT", "COMPLETE", FILE_PAGE + "arr");
        createFilelessEdition(admin, workId, "COMPLETE_SCORE", "MOVEMENT", FILE_PAGE + "mvt");

        JsonNode data = data(mockMvc.perform(get("/api/works/{id}", workId)).andExpect(status().isOk()));

        assertThat(data.path("imslpCandidateEdition").isNull())
                .as("편곡·발췌를 '우리가 고른 판본' 이라며 내보내지 않는다 (기획 §F3-7)")
                .isTrue();
    }

    @Test
    @DisplayName("판본이 아예 없는 곡도 null — 키는 생략하지 않는다 (§0-4)")
    void workWithoutEditionsHasNullCandidate() throws Exception {
        Tokens admin = loginAdmin();
        long workId = createWork(admin, createComposer(admin));

        JsonNode data = data(mockMvc.perform(get("/api/works/{id}", workId)).andExpect(status().isOk()));

        assertThat(data.has("imslpCandidateEdition")).isTrue();
        assertThat(data.path("imslpCandidateEdition").isNull()).isTrue();
    }

    /** 파일 없는(정보만) 판본 — 준비 중 곡의 실제 모습이다. */
    private long createFilelessEdition(Tokens admin, long workId, String kind, String scope, String imslpFileUrl)
            throws Exception {
        Map<String, Object> body = editionBody(null, null, null, "UNKNOWN", null);
        body.put("kind", kind);
        body.put("scope", scope);
        body.put("movementNumber", "MOVEMENT".equals(scope) ? 2 : null);
        body.put("arranger", "ARRANGEMENT".equals(kind) ? "Test Arranger" : null);
        body.put("publisher", "Breitkopf & Härtel");
        body.put("publishYear", 1862);
        body.put("imslpFileUrl", imslpFileUrl);
        return createEdition(admin, workId, body);
    }
}
