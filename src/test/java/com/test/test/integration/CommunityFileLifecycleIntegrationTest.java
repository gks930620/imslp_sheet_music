package com.test.test.integration;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 첨부·본문 파일 생명주기 (code-convention §5-3-1) — TDD Red.
 *
 * <p>기존 엔드포인트만 사용한다(계약 불변): 파일 업로드/조회는 {@code /api/files},
 * 글 생성/수정/삭제는 {@code /api/communities}. 파일 연결·삭제는 응답으로 관찰한다.
 *
 * <ul>
 *   <li>① reconcile(create/update): 본문 HTML의 {@code /uploads/{저장파일명}} 을 파싱해
 *       본문에 실제로 쓰인 파일만 {@code refId=글ID}(usage=IMAGES)로 연결.</li>
 *   <li>② 삭제 연동: 글 삭제 시 {@code refId=글ID} 파일(IMAGES+ATTACHMENT)의 메타행을 모두 삭제.</li>
 * </ul>
 */
class CommunityFileLifecycleIntegrationTest extends ApiIntegrationTestSupport {

    // ── ① create reconcile ─────────────────────────────────────────────
    // 본문에 쓰인 이미지만 글ID로 연결되고, 본문에서 빠진 초안 이미지는 연결되지 않아야 한다.
    @Test
    void create_links_only_body_referenced_images_to_post() throws Exception {
        Tokens tokens = loginDefaultUser();

        String usedName = "used-" + shortId() + ".png";
        String unusedName = "unused-" + shortId() + ".png";
        String usedPath = uploadFile(tokens, "0", "IMAGES", usedName, MediaType.IMAGE_PNG_VALUE);
        String unusedPath = uploadFile(tokens, "0", "IMAGES", unusedName, MediaType.IMAGE_PNG_VALUE);

        // 본문에는 usedPath 만 포함 (unusedPath 는 업로드만 하고 본문에서 뺌)
        long postId = createCommunity(tokens, "reconcile-create",
                "<p>hello</p><img src=\"" + usedPath + "\" />");

        JsonNode images = fileDetails(postId, "IMAGES");
        assertThat(containsStoredName(images, storedName(usedPath)))
                .as("본문에 쓰인 이미지는 refId=글ID(IMAGES)로 연결돼야 한다")
                .isTrue();
        assertThat(containsStoredName(images, storedName(unusedPath)))
                .as("본문에서 빠진 초안 이미지는 글에 연결되지 않아야 한다")
                .isFalse();
    }

    // ── ① update reconcile ─────────────────────────────────────────────
    // 수정 본문에 새로 넣은 이미지가 글ID로 연결돼야 한다.
    @Test
    void update_links_newly_referenced_body_image_to_post() throws Exception {
        Tokens tokens = loginDefaultUser();

        long postId = createCommunity(tokens, "reconcile-update", "<p>no image yet</p>");

        String addedName = "added-" + shortId() + ".png";
        String addedPath = uploadFile(tokens, "0", "IMAGES", addedName, MediaType.IMAGE_PNG_VALUE);

        updateCommunity(tokens, postId, "reconcile-update",
                "<p>now with image</p><img src=\"" + addedPath + "\" />");

        JsonNode images = fileDetails(postId, "IMAGES");
        assertThat(containsStoredName(images, storedName(addedPath)))
                .as("수정 본문에 새로 넣은 이미지가 refId=글ID(IMAGES)로 연결돼야 한다")
                .isTrue();
    }

    // ── ② 삭제 연동 ────────────────────────────────────────────────────
    // 글 삭제 시 글ID로 연결된 파일(본문 IMAGES + 첨부 ATTACHMENT) 메타행이 모두 사라져야 한다.
    @Test
    void deleting_post_removes_its_linked_files() throws Exception {
        Tokens tokens = loginDefaultUser();

        long postId = createCommunity(tokens, "delete-cascade", "<p>body</p>");

        // reconcile 의존 없이 삭제연동만 검증하기 위해 글ID로 직접 연결된 파일 2개를 만든다.
        String imgName = "img-" + shortId() + ".png";
        String attName = "att-" + shortId() + ".txt";
        uploadFile(tokens, String.valueOf(postId), "IMAGES", imgName, MediaType.IMAGE_PNG_VALUE);
        uploadFile(tokens, String.valueOf(postId), "ATTACHMENT", attName, MediaType.TEXT_PLAIN_VALUE);

        long attFileId = fileId(postId, "ATTACHMENT", attName);

        // sanity: 삭제 전에는 파일 2개가 조회되고 다운로드도 된다
        assertThat(fileDetails(postId, null).size())
                .as("삭제 전 글에 연결된 파일 2개").isEqualTo(2);
        mockMvc.perform(get("/api/files/{fileId}/content", attFileId))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/communities/{id}", postId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokens.accessToken())))
                .andExpect(status().isOk());

        assertThat(fileDetails(postId, null).size())
                .as("글 삭제 시 연결 파일 메타행이 모두 삭제돼야 한다").isZero();
        mockMvc.perform(get("/api/files/{fileId}/content", attFileId))
                .andExpect(status().isNotFound());
    }

    // ===== helpers =====

    /** 파일 업로드 후 저장 웹경로(/uploads/{저장파일명})를 반환. */
    private String uploadFile(Tokens tokens, String refId, String usage,
                              String originalFilename, String contentType) throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "files", originalFilename, contentType,
                ("bytes-" + originalFilename).getBytes(StandardCharsets.UTF_8));

        MvcResult result = mockMvc.perform(multipart("/api/files")
                        .file(file)
                        .param("refId", refId)
                        .param("refType", "COMMUNITY")
                        .param("usage", usage)
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokens.accessToken())))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
        return data.get(0).asText();
    }

    private long createCommunity(Tokens tokens, String title, String content) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/communities")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokens.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(title, content)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").asLong();
    }

    private void updateCommunity(Tokens tokens, long postId, String title, String content) throws Exception {
        mockMvc.perform(put("/api/communities/{id}", postId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokens.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(title, content)))
                .andExpect(status().isOk());
    }

    private JsonNode fileDetails(long refId, String usage) throws Exception {
        MockHttpServletRequestBuilder req = get("/api/files")
                .param("refId", String.valueOf(refId))
                .param("refType", "COMMUNITY");
        if (usage != null) {
            req = req.param("usage", usage);
        }
        MvcResult result = mockMvc.perform(req)
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
    }

    private long fileId(long refId, String usage, String originalFilename) throws Exception {
        for (JsonNode f : fileDetails(refId, usage)) {
            if (originalFilename.equals(f.path("originalFileName").asText())) {
                return f.path("fileId").asLong();
            }
        }
        throw new AssertionError("업로드된 파일을 찾을 수 없습니다: " + originalFilename);
    }

    private String json(String title, String content) throws Exception {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("title", title);
        body.put("content", content);
        return objectMapper.writeValueAsString(body);
    }

    private static String shortId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private static String storedName(String webPath) {
        return webPath.substring(webPath.lastIndexOf('/') + 1);
    }

    private static boolean containsStoredName(JsonNode details, String storedName) {
        for (JsonNode d : details) {
            if (storedName.equals(d.path("storedFileName").asText())) {
                return true;
            }
        }
        return false;
    }
}
