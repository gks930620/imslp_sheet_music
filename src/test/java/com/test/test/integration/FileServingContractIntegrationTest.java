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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 파일 서빙 계약 회귀 가드 — code-convention §5-3 (Railway Buckets 전환).
 *
 * <p><b>이 테스트는 Red가 아니라 "계약 잠금(green→green)"이다.</b>
 * 저장소를 로컬 디스크 정적 서빙 → 버킷 프록시로 바꾸는 리팩터 동안,
 * 프론트 계약인 {@code GET /uploads/{저장파일명}} 이 계속 <b>업로드한 바이트 그대로</b>
 * 200으로 서빙되는지를 고정한다.
 *
 * <p>현재는 {@code WebConfig.addResourceHandlers} 정적 서빙으로 통과한다.
 * backend-dev가 활성 저장전략(로컬/버킷) 바이트 스트리밍 프록시로 교체해도 이 테스트는 계속 통과해야 한다.
 * (버킷 모드는 외부 S3라 H2 통합테스트로 못 돌리므로, 로컬 폴백 경로로 계약만 고정한다.)
 */
class FileServingContractIntegrationTest extends ApiIntegrationTestSupport {

    @Test
    void uploads_path_serves_stored_file_bytes() throws Exception {
        Tokens tokens = loginDefaultUser();
        long postId = createCommunity(tokens);

        byte[] payload = ("serve-me-" + UUID.randomUUID()).getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile(
                "files", "serve.png", MediaType.IMAGE_PNG_VALUE, payload);

        MvcResult upload = mockMvc.perform(multipart("/api/files")
                        .file(file)
                        .param("refId", String.valueOf(postId))
                        .param("refType", "COMMUNITY")
                        .param("usage", "IMAGES")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokens.accessToken())))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode data = objectMapper.readTree(upload.getResponse().getContentAsString()).path("data");
        String webPath = data.get(0).asText(); // /uploads/{저장파일명}
        assertThat(webPath).startsWith("/uploads/");

        // 프론트 계약: 같은 오리진 GET /uploads/{저장파일명} → 원본 바이트 200
        MvcResult served = mockMvc.perform(get(webPath))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(served.getResponse().getContentAsByteArray())
                .as("서빙된 바이트는 업로드한 원본과 동일해야 한다")
                .isEqualTo(payload);
    }

    private long createCommunity(Tokens tokens) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/communities")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokens.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "serving-owner",
                                  "content": "owner post"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").asLong();
    }
}
