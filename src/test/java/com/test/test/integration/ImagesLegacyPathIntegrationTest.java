package com.test.test.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.test.test.integration.support.AdminApiTestSupport;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 폐기된 {@code GET /images/{filename}} 경로 — 02_API_명세서 §0-4 (qa 4차 결함).
 *
 * <p><b>왜 이 테스트가 있는가.</b> {@link UploadsServingGateIntegrationTest} 가 {@code /uploads} 뒷문을 닫은 뒤에도
 * 보일러플레이트가 남긴 {@code FileController.serveFile(/images/{filename})} 이 <b>같은 바이트를 게이트 없이</b>
 * 스트리밍하고 있었다(qa 4차 실측: 비로그인 {@code GET /images/{저장파일명}.pdf} → 200 · %PDF · 판본 PDF 200개·502MB).
 * §3-4 는 {@code GET /api/editions/{id}/download} 가 판본 바이트를 얻는 <b>유일한</b> 공개 경로라고 못 박는다.
 *
 * <p><b>선택: 게이트가 아니라 경로 제거.</b> {@code /images} 는 명세서 §0-4 에 없고(바이트 프록시는 {@code /uploads} 하나),
 * 프론트 소스·빌드 번들·백엔드 코드·기존 테스트 어디에서도 참조하지 않는다. 문을 하나 줄이는 편이
 * "게이트를 또 빠뜨리는" 다음 사고를 막는다. 그래서 이 테스트는 <b>모든 종류의 파일</b>에 대해
 * {@code /images} 가 닫혔음을, 그리고 정식 경로({@code /uploads})는 그대로 열려 있음을 함께 잠근다.
 */
class ImagesLegacyPathIntegrationTest extends AdminApiTestSupport {

    private static final String PDF_MAGIC = "%PDF";

    @Test
    @DisplayName("판본 PDF: /images 로 요청해도 200·PDF 바이트가 나오면 안 된다 → 404")
    void editionPdfIsNotServedThroughImagesPath() throws Exception {
        Tokens admin = loginAdmin();
        JsonNode upload = uploadSamplePdf(admin);
        long pdfFileId = upload.path("fileId").asLong();
        long workId = createWork(admin, createComposer(admin));
        Map<String, Object> body = editionBody(pdfFileId, upload.path("previewFileId").asLong(),
                upload.path("pageCount").asInt(), "FREE", "테스트 판정 근거");
        long editionId = createEdition(admin, workId, body);
        setRecommended(admin, workId, editionId);

        // 정식 문은 열려 있다(FREE) — 그래도 뒷문은 닫혀 있어야 한다.
        mockMvc.perform(get("/api/editions/{id}/download", editionId))
                .andExpect(status().isOk());

        MvcResult result = mockMvc.perform(get(imagesPathOf(storedWebPath(pdfFileId))))
                .andExpect(status().isNotFound())
                .andReturn();

        assertThat(result.getResponse().getContentAsString(StandardCharsets.ISO_8859_1))
                .as("판본 PDF 바이트가 한 조각도 새면 안 된다")
                .doesNotContain(PDF_MAGIC);

        // 폐기된 경로는 "원래 없던 경로" 와 똑같이 보여야 한다 — 게이트가 붙은 바이트 엔드포인트(§0-4 의 빈 본문 404)가
        // 아니라 그냥 없는 경로이므로 §0-1 표준 오류 본문(NOT_FOUND)이 나온다. 두 모양이 갈리면 "막힌 것" 과
        // "없는 것" 을 호출자가 구분하게 되어, 있던 문을 다시 여는 실수를 부른다.
        String unknownPathBody = mockMvc.perform(get("/nowhere-{name}.pdf", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(errorCodeOf(result.getResponse().getContentAsString(StandardCharsets.UTF_8)))
                .as("없는 경로와 같은 404 (%s)", unknownPathBody)
                .isEqualTo(errorCodeOf(unknownPathBody))
                .isEqualTo("NOT_FOUND");
    }

    @Test
    @DisplayName("미리보기 PNG: 정식 경로 /uploads 는 200, 폐기된 /images 는 404")
    void previewPngIsServedOnlyThroughUploads() throws Exception {
        Tokens admin = loginAdmin();
        JsonNode upload = uploadSamplePdf(admin);
        String previewUrl = upload.path("previewUrl").asText();
        assertThat(previewUrl).as("미리보기 PNG 가 있어야 이 테스트가 뜻이 있다(§5-1)").endsWith(".png");

        mockMvc.perform(get(previewUrl)).andExpect(status().isOk());
        mockMvc.perform(get(imagesPathOf(previewUrl))).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("커뮤니티 이미지: 정식 경로 /uploads 는 200 + 원본 바이트, 폐기된 /images 는 404")
    void communityImageIsServedOnlyThroughUploads() throws Exception {
        Tokens tokens = loginDefaultUser();
        long postId = createCommunityPost(tokens);
        byte[] image = ("images-legacy-" + UUID.randomUUID()).getBytes(StandardCharsets.UTF_8);
        String webPath = uploadCommunityImage(tokens, postId, image);

        MvcResult served = mockMvc.perform(get(webPath))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(served.getResponse().getContentAsByteArray())
                .as("회귀 가드: 커뮤니티 이미지는 /uploads 로 계속 나와야 한다")
                .isEqualTo(image);

        mockMvc.perform(get(imagesPathOf(webPath))).andExpect(status().isNotFound());
    }

    // ===== 픽스처 =====

    /** 오류 본문의 {@code errorCode} (§0-1). 본문 모양 비교를 timestamp 없이 하기 위한 것. */
    private String errorCodeOf(String body) throws Exception {
        return objectMapper.readTree(body).path("errorCode").asText();
    }

    /** {@code /uploads/{저장파일명}} → 폐기된 {@code /images/{저장파일명}}. */
    private String imagesPathOf(String uploadsWebPath) {
        return "/images/" + uploadsWebPath.substring(uploadsWebPath.lastIndexOf('/') + 1);
    }

    private long createCommunityPost(Tokens tokens) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/communities")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokens.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "images-legacy",
                                  "content": "legacy path regression"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        return root(result).path("data").asLong();
    }

    private String uploadCommunityImage(Tokens tokens, long postId, byte[] bytes) throws Exception {
        MvcResult result = mockMvc.perform(multipart("/api/files")
                        .file(new MockMultipartFile("files", "note.png", MediaType.IMAGE_PNG_VALUE, bytes))
                        .param("refId", String.valueOf(postId))
                        .param("refType", "COMMUNITY")
                        .param("usage", "IMAGES")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokens.accessToken())))
                .andExpect(status().isCreated())
                .andReturn();
        return root(result).path("data").get(0).asText();
    }
}
