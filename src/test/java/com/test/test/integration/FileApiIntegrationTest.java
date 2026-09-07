package com.test.test.integration;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FileApiIntegrationTest extends ApiIntegrationTestSupport {

    @Test
    void files_paths_api_returns_file_paths() throws Exception {
        mockMvc.perform(get("/api/files/paths")
                        .param("refId", "1")
                        .param("refType", "COMMUNITY")
                        .param("usage", "THUMBNAIL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void files_upload_detail_content_delete_work() throws Exception {
        Tokens tokens = loginDefaultUser();
        // 소유권 검증(IDOR 방지) 도입으로, 본인이 작성한 게시글에만 업로드/삭제 가능하다.
        long communityId = createCommunity(tokens);

        String originalFilename = "integration-" + UUID.randomUUID().toString().substring(0, 8) + ".txt";

        MockMultipartFile multipartFile = new MockMultipartFile(
                "files",
                originalFilename,
                MediaType.TEXT_PLAIN_VALUE,
                "integration-file-content".getBytes(StandardCharsets.UTF_8)
        );

        mockMvc.perform(multipart("/api/files")
                        .file(multipartFile)
                        .param("refId", String.valueOf(communityId))
                        .param("refType", "COMMUNITY")
                        .param("usage", "ATTACHMENT")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokens.accessToken())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());

        long fileId = findUploadedFileId(communityId, originalFilename);

        mockMvc.perform(get("/api/files/{fileId}/content", fileId))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, org.hamcrest.Matchers.containsString("attachment;")));

        mockMvc.perform(delete("/api/files/{fileId}", fileId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokens.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    /**
     * IDOR 방지 검증: 다른 사용자는 남의 게시글에 파일을 업로드하거나 남의 파일을 삭제할 수 없다. (403)
     */
    @Test
    void other_user_cannot_upload_or_delete_files_on_someones_post() throws Exception {
        // 1) 소유자(기본 사용자)가 게시글 + 첨부 파일 생성
        Tokens owner = loginDefaultUser();
        long communityId = createCommunity(owner);

        String originalFilename = "victim-" + UUID.randomUUID().toString().substring(0, 8) + ".txt";
        MockMultipartFile multipartFile = new MockMultipartFile(
                "files", originalFilename, MediaType.TEXT_PLAIN_VALUE,
                "victim-content".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/files")
                        .file(multipartFile)
                        .param("refId", String.valueOf(communityId))
                        .param("refType", "COMMUNITY")
                        .param("usage", "ATTACHMENT")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner.accessToken())))
                .andExpect(status().isCreated());

        long fileId = findUploadedFileId(communityId, originalFilename);

        // 2) 공격자 계정 생성 및 로그인
        Tokens attacker = registerAndLogin();

        // 3) 공격자가 남의 게시글에 파일 업로드 시도 → 403
        MockMultipartFile attackFile = new MockMultipartFile(
                "files", "attack.txt", MediaType.TEXT_PLAIN_VALUE, "attack".getBytes(StandardCharsets.UTF_8));
        mockMvc.perform(multipart("/api/files")
                        .file(attackFile)
                        .param("refId", String.valueOf(communityId))
                        .param("refType", "COMMUNITY")
                        .param("usage", "ATTACHMENT")
                        .header(HttpHeaders.AUTHORIZATION, bearer(attacker.accessToken())))
                .andExpect(status().isForbidden());

        // 4) 공격자가 남의 파일 삭제 시도 → 403
        mockMvc.perform(delete("/api/files/{fileId}", fileId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(attacker.accessToken())))
                .andExpect(status().isForbidden());
    }

    // ===== helpers =====

    private long createCommunity(Tokens tokens) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/communities")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokens.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "file-owner-post",
                                  "content": "file-owner-content"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode created = objectMapper.readTree(result.getResponse().getContentAsString());
        return created.path("data").asLong();
    }

    private long findUploadedFileId(long communityId, String originalFilename) throws Exception {
        MvcResult detailResult = mockMvc.perform(get("/api/files")
                        .param("refId", String.valueOf(communityId))
                        .param("refType", "COMMUNITY")
                        .param("usage", "ATTACHMENT"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode details = objectMapper.readTree(detailResult.getResponse().getContentAsString()).path("data");
        for (JsonNode detail : details) {
            if (originalFilename.equals(detail.path("originalFileName").asText())) {
                long fileId = detail.path("fileId").asLong();
                assertThat(fileId).isPositive();
                return fileId;
            }
        }
        throw new AssertionError("업로드된 파일을 찾을 수 없습니다: " + originalFilename);
    }

    private Tokens registerAndLogin() throws Exception {
        String username = "attacker_" + UUID.randomUUID().toString().substring(0, 8);
        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "%s",
                                  "password": "1234",
                                  "email": "%s@example.com",
                                  "nickname": "attacker"
                                }
                                """.formatted(username, username)))
                .andExpect(status().isCreated());

        MvcResult loginResult = mockMvc.perform(post("/api/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "%s",
                                  "password": "1234"
                                }
                                """.formatted(username)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode root = objectMapper.readTree(loginResult.getResponse().getContentAsString());
        return new Tokens(root.path("access_token").asText(), root.path("refresh_token").asText());
    }
}
