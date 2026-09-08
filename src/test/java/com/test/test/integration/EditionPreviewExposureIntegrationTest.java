package com.test.test.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.test.test.integration.support.AdminApiTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 미리보기 노출 계약 — 02_API_명세서 §0-4 · §2-3 (2026-09-08 신설, senior-dev. qa 4차 결함 2. <b>Red</b>).
 *
 * <h2>계약이 없었다</h2>
 * §0-4 표는 판본 미리보기 PNG(`ref_type=EDITION`, `file_usage=THUMBNAIL`)를 <b>무조건 200</b> 으로 적었고,
 * 기획 §F3-6 · §5 예외표는 <b>"확인 중·이용 제한 판본은 미리보기도 감춘다"</b> 고 정했다. 두 문서가 다른 말을 했고
 * 코드는 §0-4 를 따랐다 — qa 4차 실측: 실데이터의 UNKNOWN 판본 10건이 공개 응답에 {@code previewUrl} 을 싣고
 * 직접 GET 도 200 이었다. {@code EditionDtoAssembler.previewUrl()} 에 판정 조건이 없다.
 *
 * <h2>확정: 응답과 서빙을 <b>둘 다</b> 막는다</h2>
 * <ol>
 *   <li><b>응답</b> — 공개 응답의 {@code previewUrl} 은 {@code koreaCopyright == FREE} 일 때만 값이 있다.
 *       그렇지 않으면 {@code null}(§2-3). 화면이 문구를 고르려면 응답이 진실을 말해야 한다: 기획 §5 예외표는
 *       "미리보기 준비 중"(파일 없음)과 "저작권을 확인하는 중이라 미리보기도 아직 보여드릴 수 없어요"(판정 안 끝남)를
 *       <b>다른 문구</b>로 정했고, 두 경우 모두 {@code previewUrl == null} 이므로 화면은 {@code koreaCopyright} 로 가른다.</li>
 *   <li><b>서빙</b> — {@code GET /uploads/{저장파일명}} 도 비 FREE 판본의 미리보기면 <b>404</b>(비관리자).
 *       응답만 비우면 <b>저장 파일명을 아는 사람에게는 그대로 열려 있다.</b> 이름은 없어지지 않는다:
 *       FREE 였다가 판정이 뒤집힌 판본의 옛 응답·브라우저 기록·캐시, 서버의 {@code uploads/} 폴더(qa 3차가 실제로 쓴 수단).
 *       "우리 응답에 안 실렸으니 닫힌 것" 이라는 판단이 정확히 {@code /images} 사고였다(02 §0-4).
 *       재배포 책임(기획 {@code 02_저작권_판정_지침} A-4)은 <b>우리 도메인이 그 바이트를 주느냐</b>로 정해지지,
 *       우리 JSON 이 주소를 알려줬느냐로 정해지지 않는다.</li>
 * </ol>
 * 하나만으로는 둘 다 못 한다 — 응답만 막으면 바이트가 열려 있고, 서빙만 막으면 화면에 깨진 이미지가 뜬다.
 *
 * <h2>관리 화면은 계속 본다</h2>
 * 판정 근거가 <b>미리보기 그 자체</b>다(기획 §F6-4 (B)3 "추천 판본의 미리보기를 열어 피아노 악보가 맞는지 확인").
 * 판정 안 된 판본의 미리보기를 관리자에게 감추면 판정 자체가 불가능해진다. 그래서
 * {@code AdminEditionDTO.previewUrl}(§4-7 · §5-4)은 판정과 무관하게 그대로이고, {@code /uploads} 게이트도
 * <b>ADMIN 권한이면 통과</b>한다. 관리 웹은 쿠키 인증({@code credentials: "include"})이라 {@code <img src>} 로도 실린다.
 */
class EditionPreviewExposureIntegrationTest extends AdminApiTestSupport {

    @Test
    @DisplayName("공개 곡 상세: FREE 판본만 previewUrl 이 있고 확인 중·이용 제한 판본은 null 이다")
    void publicDetailHidesPreviewUrlOfUnjudgedEditions() throws Exception {
        Tokens admin = loginAdmin();
        long workId = createWork(admin, createComposer(admin));

        long unknown = createFileEdition(admin, workId, "UNKNOWN", null);
        long restricted = createFileEdition(admin, workId, "RESTRICTED", "테스트 판정 근거");
        long free = createFileEdition(admin, workId, "FREE", "테스트 판정 근거");
        setRecommended(admin, workId, unknown);

        JsonNode data = data(mockMvc.perform(get("/api/works/{id}", workId)).andExpect(status().isOk()));

        assertThat(data.path("recommendedEdition").path("previewUrl").isNull())
                .as("추천 판본이라도 판정이 안 끝났으면 미리보기를 감춘다 (기획 §F3-6)")
                .isTrue();
        assertThat(previewUrlOf(data.path("otherEditions"), restricted))
                .as("이용 제한 판본도 같다 — 다운로드를 막는 이유가 1쪽 이미지에도 그대로 적용된다")
                .isNull();
        assertThat(previewUrlOf(data.path("otherEditions"), free))
                .as("FREE 는 그대로 보인다 — '받기 전에 스캔 화질을 본다'는 약속(기획 §F3-6 반대 방향)")
                .endsWith(".png");
    }

    @Test
    @DisplayName("검색 결과의 곡 카드 previewUrl 도 같은 규칙 — 추천 판본이 비 FREE 면 null")
    void searchCardHidesPreviewUrlOfUnjudgedRecommendation() throws Exception {
        Tokens admin = loginAdmin();
        String uniq = uniq();
        String titleKo = "미리보기계약 " + uniq;
        long workId = createWork(admin, workBody(createComposer(admin), titleKo, "Preview Contract " + uniq));
        long unknown = createFileEdition(admin, workId, "UNKNOWN", null);
        setRecommended(admin, workId, unknown);

        JsonNode card = searchCard(titleKo, workId);
        assertThat(card.path("previewUrl").isNull())
                .as("곡 카드의 미리보기는 추천 판본의 첫 페이지다(§2-2) — 같은 판정 규칙을 받는다")
                .isTrue();

        judge(admin, unknown, "FREE");
        assertThat(searchCard(titleKo, workId).path("previewUrl").asText())
                .as("FREE 로 판정하면 그때 보인다")
                .endsWith(".png");
    }

    @Test
    @DisplayName("/uploads 미리보기 게이트: 비 FREE 판본의 PNG 는 비로그인·USER 에게 404, ADMIN 에게만 200")
    void uploadsServesUnjudgedPreviewOnlyToAdmin() throws Exception {
        Tokens admin = loginAdmin();
        Tokens user = loginUser();
        long workId = createWork(admin, createComposer(admin));

        JsonNode unknownUpload = uploadSamplePdf(admin);
        createEdition(admin, workId, editionBody(unknownUpload.path("fileId").asLong(),
                unknownUpload.path("previewFileId").asLong(), unknownUpload.path("pageCount").asInt(),
                "UNKNOWN", null));
        String unknownPreview = unknownUpload.path("previewUrl").asText();

        JsonNode freeUpload = uploadSamplePdf(admin);
        createEdition(admin, workId, editionBody(freeUpload.path("fileId").asLong(),
                freeUpload.path("previewFileId").asLong(), freeUpload.path("pageCount").asInt(),
                "FREE", "테스트 판정 근거"));
        String freePreview = freeUpload.path("previewUrl").asText();

        mockMvc.perform(get(unknownPreview))
                .andExpect(status().isNotFound());
        mockMvc.perform(get(unknownPreview).header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
                .andExpect(status().isNotFound());
        mockMvc.perform(get(unknownPreview).header(HttpHeaders.AUTHORIZATION, bearer(admin.accessToken())))
                .andExpect(status().isOk());

        mockMvc.perform(get(freePreview))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("판정이 FREE 에서 되돌려지면 이미 알려진 저장 파일명도 그 즉시 닫힌다")
    void revokedJudgementClosesAlreadyKnownPreviewName() throws Exception {
        Tokens admin = loginAdmin();
        long workId = createWork(admin, createComposer(admin));
        JsonNode upload = uploadSamplePdf(admin);
        long editionId = createEdition(admin, workId, editionBody(upload.path("fileId").asLong(),
                upload.path("previewFileId").asLong(), upload.path("pageCount").asInt(),
                "FREE", "테스트 판정 근거"));
        setRecommended(admin, workId, editionId);

        // 누구나 이 시점에 주소를 손에 넣을 수 있다(공개 응답에 실려 있다).
        String previewUrl = data(mockMvc.perform(get("/api/works/{id}", workId)).andExpect(status().isOk()))
                .path("recommendedEdition").path("previewUrl").asText();
        mockMvc.perform(get(previewUrl)).andExpect(status().isOk());

        judge(admin, editionId, "UNKNOWN");

        assertThat(data(mockMvc.perform(get("/api/works/{id}", workId)).andExpect(status().isOk()))
                .path("recommendedEdition").path("previewUrl").isNull())
                .as("응답에서 사라지고")
                .isTrue();
        // 그리고 주소를 이미 아는 사람에게도 닫힌다 — 이게 응답만 막으면 안 되는 이유다.
        mockMvc.perform(get(previewUrl)).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("관리 화면은 판정과 무관하게 미리보기를 본다 — 미리보기가 판정 근거이기 때문이다")
    void adminKeepsPreviewRegardlessOfJudgement() throws Exception {
        Tokens admin = loginAdmin();
        long workId = createWork(admin, createComposer(admin));
        long editionId = createFileEdition(admin, workId, "UNKNOWN", null);

        JsonNode adminEditions = getWork(admin, workId).path("editions");
        assertThat(previewUrlOf(adminEditions, editionId))
                .as("§4-7 곡 상세(관리) — 여기서 미리보기를 열어 피아노 악보인지 본다(기획 §F6-4)")
                .endsWith(".png");

        JsonNode adminEdition = data(adminGet(admin, "/api/admin/editions/{id}", editionId)
                .andExpect(status().isOk()));
        assertThat(adminEdition.path("previewUrl").asText())
                .as("§5-4 판본 상세(관리)도 같다")
                .endsWith(".png");
    }

    // ===== 헬퍼 =====

    /** 판본 배열에서 id 로 찾은 항목의 previewUrl (없으면 AssertionError, null 이면 null). */
    private String previewUrlOf(JsonNode editions, long editionId) {
        for (JsonNode edition : editions) {
            if (edition.path("id").asLong() == editionId) {
                JsonNode preview = edition.path("previewUrl");
                return preview.isNull() ? null : preview.asText();
            }
        }
        throw new AssertionError("판본 %d 가 목록에 없다: %s".formatted(editionId, editions));
    }

    private JsonNode searchCard(String q, long workId) throws Exception {
        JsonNode content = data(mockMvc.perform(get("/api/works/search").param("q", q))
                .andExpect(status().isOk())).path("works").path("content");
        for (JsonNode card : content) {
            if (card.path("id").asLong() == workId) {
                return card;
            }
        }
        throw new AssertionError("검색 결과에 곡 %d 가 없다: %s".formatted(workId, content));
    }

    /** PUT /api/admin/editions/{id}/copyright (§5-9). */
    private void judge(Tokens admin, long editionId, String koreaCopyright) throws Exception {
        adminPut(admin, "/api/admin/editions/{id}/copyright",
                json("koreaCopyright", koreaCopyright,
                        "copyrightNote", "UNKNOWN".equals(koreaCopyright) ? null : "테스트 판정 근거"),
                editionId)
                .andExpect(status().isOk());
    }
}
