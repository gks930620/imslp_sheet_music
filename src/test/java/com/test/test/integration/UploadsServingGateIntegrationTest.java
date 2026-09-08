package com.test.test.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.test.test.integration.support.AdminApiTestSupport;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code GET /uploads/{저장파일명}} 서빙 게이트 — 02_API_명세서 §0-4 · §3-4 (2026-09-08 확정).
 *
 * <p><b>왜 이 테스트가 있는가 (qa 3차 결함 1).</b> §3-4 는 "이 API(다운로드)가 판본 바이트를 얻는
 * <b>유일한</b> 공개 경로" 라고 못 박았는데, {@code FileServingController.serve} 는 {@code files} 행을
 * 아예 조회하지 않아 저장 파일명만 알면 <b>비로그인·헤더 없이 판본 PDF 원본 전량</b>(80개·515MB)을
 * 받을 수 있었다. 같은 판본이 {@code /api/editions/{id}/download} 에서는 403 {@code COPYRIGHT_RESTRICTED} 다 —
 * 게이트가 한쪽 문에만 달려 있으면 게이트가 없는 것과 같다.
 *
 * <p><b>확정 계약</b>
 * <ul>
 *   <li>{@code /uploads/**} 는 <b>미리보기 PNG·커뮤니티·사용자 파일 전용</b>이다.
 *       {@code files.ref_type = EDITION} 인 PDF(판본 원본)는 <b>항상 404</b> —
 *       판정이 FREE 여도 마찬가지다(§3-4 가 여는 문은 하나뿐이고, 다운로드 수도 그 문에서만 센다).</li>
 *   <li>{@code files} 행이 없는 저장 파일명도 404 — 업로드 폴더에 남은 고아 바이트를 내주지 않는다.
 *       모든 저장 경로가 행을 함께 쓰므로, 행이 없는 바이트는 우리가 책임지는 파일이 아니다.</li>
 *   <li>404 응답은 <b>본문 없이</b> 준다. {@code /uploads} 는 {@code <img src>}·PDF 뷰어가 직접 무는
 *       바이트 엔드포인트라 §0-1 의 JSON 래퍼를 쓰지 않으며, 한 엔드포인트가 404 를 두 모양으로 내려주면
 *       호출자가 분기해야 한다(바이트가 없을 때도 지금 빈 404 다).</li>
 *   <li>미리보기 PNG({@code previewUrl})·커뮤니티 이미지·첨부는 <b>그대로 200</b>. 게이트가 판본 파일을
 *       통째로 막아 버리면 검색 카드·라이트박스가 전부 깨진다.</li>
 * </ul>
 *
 * <p>바이트 동일성 회귀는 {@link FileServingContractIntegrationTest} 가 잠근다 —
 * 여기서는 "무엇이 열리고 무엇이 닫히는가" 만 본다.
 */
class UploadsServingGateIntegrationTest extends AdminApiTestSupport {

    private static final String PDF_MAGIC = "%PDF";

    /** 판본 1개(파일 있음) + 그 파일들의 웹 경로. */
    private record EditionFiles(long editionId, String pdfWebPath, String previewWebPath) {
    }

    @Test
    @DisplayName("저작권 제한 판본: 다운로드 API 는 403 인데 /uploads 로 200 이면 안 된다 → 404, 본문 없음")
    void restrictedEditionPdfIsNotServedThroughUploadsProxy() throws Exception {
        Tokens admin = loginAdmin();
        EditionFiles files = createEditionWithFile(admin, "RESTRICTED", "테스트 판정 근거");

        // 정식 문은 닫혀 있다 (§3-4)
        mockMvc.perform(get("/api/editions/{id}/download", files.editionId()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("COPYRIGHT_RESTRICTED"));

        // 뒷문도 닫혀 있어야 한다 — 비로그인·헤더 없이 저장 파일명만 아는 상황
        MvcResult result = mockMvc.perform(get(files.pdfWebPath()))
                .andExpect(status().isNotFound())
                .andReturn();

        String body = result.getResponse().getContentAsString(StandardCharsets.ISO_8859_1);
        assertThat(body)
                .as("판본 PDF 바이트가 한 조각도 새면 안 된다")
                .doesNotContain(PDF_MAGIC);
        assertThat(body)
                .as("§0-4: /uploads 의 404 는 본문 없이 준다")
                .isEmpty();
    }

    @Test
    @DisplayName("판정이 FREE 인 판본도 /uploads 로는 404 — 다운로드 API 가 유일한 경로이고 다운로드 수도 거기서만 센다")
    void freeEditionPdfIsAlsoNotServedThroughUploadsProxy() throws Exception {
        Tokens admin = loginAdmin();
        EditionFiles files = createEditionWithFile(admin, "FREE", "테스트 판정 근거");

        mockMvc.perform(get("/api/editions/{id}/download", files.editionId()))
                .andExpect(status().isOk());

        mockMvc.perform(get(files.pdfWebPath()))
                .andExpect(status().isNotFound());

        // 판정은 언제든 FREE → RESTRICTED 로 바뀔 수 있다. 그때 이미 열려 있던 뒷문이 있으면 안 된다.
        assertThat(getEdition(admin, files.editionId()).path("downloadCount").asLong())
                .as("프록시로 받아 간 것은 다운로드 수에 잡히지 않는다 — 그래서 프록시를 열어 두면 안 된다")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("미리보기 PNG 는 계속 200 image/png — 게이트가 판본 파일을 통째로 막으면 화면이 깨진다")
    void previewPngIsStillServed() throws Exception {
        Tokens admin = loginAdmin();
        EditionFiles files = createEditionWithFile(admin, "RESTRICTED", "테스트 판정 근거");

        MvcResult result = mockMvc.perform(get(files.previewWebPath()))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, MediaType.IMAGE_PNG_VALUE))
                .andReturn();

        assertThat(result.getResponse().getContentAsByteArray())
                .as("미리보기는 저작권 제한 판본이어도 보여 준다(§3-3 — 표지 한 장은 '무엇인지 알아보는' 정보다)")
                .isNotEmpty();
    }

    @Test
    @DisplayName("회귀 가드: 커뮤니티 이미지·첨부는 계속 200 + 원본 바이트")
    void communityImagesAndAttachmentsAreStillServed() throws Exception {
        Tokens tokens = loginDefaultUser();
        long postId = createCommunityPost(tokens);

        byte[] image = ("uploads-image-" + UUID.randomUUID()).getBytes(StandardCharsets.UTF_8);
        byte[] attachment = ("uploads-attachment-" + UUID.randomUUID()).getBytes(StandardCharsets.UTF_8);

        String imagePath = uploadCommunityFile(tokens, postId, "IMAGES",
                new MockMultipartFile("files", "note.png", MediaType.IMAGE_PNG_VALUE, image));
        String attachmentPath = uploadCommunityFile(tokens, postId, "ATTACHMENT",
                new MockMultipartFile("files", "note.txt", MediaType.TEXT_PLAIN_VALUE, attachment));

        assertThat(served(imagePath)).isEqualTo(image);
        assertThat(served(attachmentPath)).isEqualTo(attachment);
    }

    @Test
    @DisplayName("files 행이 없는 저장 파일명 → 404 — 업로드 폴더에 남은 고아 바이트도, 없는 이름도 내주지 않는다")
    void bytesWithoutFileRowAreNotServed() throws Exception {
        Path uploadRoot = Path.of("build", "test-uploads").toAbsolutePath().normalize();
        Files.createDirectories(uploadRoot);
        String strayName = "stray-" + UUID.randomUUID() + ".pdf";
        Files.write(uploadRoot.resolve(strayName), PDF_MAGIC.getBytes(StandardCharsets.US_ASCII));

        // 판본 삭제(§5-5) 뒤 바이트 삭제만 실패하는 일이 실제로 생긴다. 행이 없으면 우리 파일이 아니다.
        mockMvc.perform(get("/uploads/{name}", strayName))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/uploads/{name}", "does-not-exist-" + UUID.randomUUID() + ".png"))
                .andExpect(status().isNotFound());
    }

    // ===== 픽스처 =====

    private EditionFiles createEditionWithFile(Tokens admin, String koreaCopyright, String note) throws Exception {
        long composerId = createComposer(admin);
        long workId = createWork(admin, composerId);

        JsonNode upload = uploadSamplePdf(admin);
        long pdfFileId = upload.path("fileId").asLong();
        assertThat(upload.path("previewFileId").isNull())
                .as("미리보기 PNG 가 있어야 이 테스트가 뜻이 있다(§5-1)")
                .isFalse();

        Map<String, Object> body = editionBody(pdfFileId, upload.path("previewFileId").asLong(),
                upload.path("pageCount").asInt(), koreaCopyright, note);
        long editionId = createEdition(admin, workId, body);
        setRecommended(admin, workId, editionId);

        return new EditionFiles(editionId, storedWebPath(pdfFileId), upload.path("previewUrl").asText());
    }

    private long createCommunityPost(Tokens tokens) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/communities")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokens.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "uploads-gate",
                                  "content": "gate regression"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        return root(result).path("data").asLong();
    }

    /** POST /api/files → 응답의 첫 웹 경로({@code /uploads/{저장파일명}}). */
    private String uploadCommunityFile(Tokens tokens, long postId, String usage, MockMultipartFile part)
            throws Exception {
        MvcResult result = mockMvc.perform(multipart("/api/files")
                        .file(part)
                        .param("refId", String.valueOf(postId))
                        .param("refType", "COMMUNITY")
                        .param("usage", usage)
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokens.accessToken())))
                .andExpect(status().isCreated())
                .andReturn();
        return root(result).path("data").get(0).asText();
    }

    private byte[] served(String webPath) throws Exception {
        return mockMvc.perform(get(webPath))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsByteArray();
    }
}
