package com.test.test.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CommunityAndCommentApiIntegrationTest extends ApiIntegrationTestSupport {

    @Test
    void communities_get_list_and_detail_work() throws Exception {
        mockMvc.perform(get("/api/communities")
                        .param("page", "0")
                        .param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content").isArray());

        mockMvc.perform(get("/api/communities/{communityId}", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1));
    }

    @Test
    void communities_create_update_delete_work() throws Exception {
        Tokens tokens = loginDefaultUser();

        MvcResult createResult = mockMvc.perform(post("/api/communities")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokens.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "integration-title",
                                  "content": "integration-content"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, org.hamcrest.Matchers.containsString("/api/communities/")))
                .andExpect(jsonPath("$.success").value(true))
                .andReturn();

        JsonNode created = objectMapper.readTree(createResult.getResponse().getContentAsString());
        long communityId = created.path("data").asLong();
        assertThat(communityId).isPositive();

        mockMvc.perform(put("/api/communities/{communityId}", communityId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokens.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "updated-title",
                                  "content": "updated-content"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(delete("/api/communities/{communityId}", communityId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokens.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void comments_get_create_update_delete_work() throws Exception {
        Tokens tokens = loginDefaultUser();

        mockMvc.perform(get("/api/communities/{communityId}/comments", 1L)
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content").isArray());

        MvcResult createResult = mockMvc.perform(post("/api/communities/{communityId}/comments", 1L)
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokens.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "content": "integration comment"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").isNumber())
                .andReturn();

        JsonNode created = objectMapper.readTree(createResult.getResponse().getContentAsString());
        long commentId = created.path("data").path("id").asLong();
        assertThat(commentId).isPositive();

        mockMvc.perform(put("/api/comments/{commentId}", commentId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokens.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "content": "updated integration comment"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(commentId));

        mockMvc.perform(delete("/api/comments/{commentId}", commentId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokens.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
    // ===== qa 2차 결함 — 사용자 문구에 영문 리소스명이 남아 있다 (02 §0-2) =====

    /**
     * {@code NOT_FOUND}·{@code ACCESS_DENIED} 문구의 리소스명은 <b>사용자가 읽는 한국어 낱말</b>이어야 한다(02 §0-2).
     * 조사 계산은 D12 에서 고쳐졌지만 {@code CommentService} 가 아직 {@code "Comment"}·{@code "Community"}·{@code "User"} 를 넘긴다.
     * 곡·작곡가·게시글·채팅방은 이미 한글이라 댓글만 남았다 — 화면에 그대로 보이는 문구다.
     */
    @Test
    void comment_error_messages_use_korean_resource_names() throws Exception {
        Tokens owner = loginUser();          // user4 가 쓴 댓글
        Tokens other = loginDefaultUser();   // gks930620 이 남의 댓글을 건드린다

        mockMvc.perform(delete("/api/comments/{commentId}", 99_999_999L)
                        .header(HttpHeaders.AUTHORIZATION, bearer(other.accessToken())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("댓글을 찾을 수 없어요"));

        mockMvc.perform(post("/api/communities/{communityId}/comments", 99_999_999L)
                        .header(HttpHeaders.AUTHORIZATION, bearer(other.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "content": "없는 게시글에 댓글"
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("게시글을 찾을 수 없어요"));

        MvcResult createResult = mockMvc.perform(post("/api/communities/{communityId}/comments", 1L)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "content": "남의 댓글"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        long commentId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .path("data").path("id").asLong();

        mockMvc.perform(put("/api/comments/{commentId}", commentId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(other.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "content": "남의 댓글 수정 시도"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"))
                .andExpect(jsonPath("$.message").value("본인의 댓글만 수정할 수 있습니다."));
    }

    // ===== qa 2차 결함 — 항상 빈 배열인 죽은 필드 (03 §14-2) =====

    /**
     * {@code CommunityDTO.imageUrls}/{@code attachments} 는 목록·상세 <b>모든 경로에서 항상 {@code []}</b> 다
     * (서비스가 {@code List.of(), List.of()} 를 하드코딩). 화면은 첨부를 {@code /api/files} 로 따로 받는다.
     *
     * <p>항상 비어 있는 필드는 없는 필드보다 나쁘다 — "첨부가 없다" 고 <b>거짓말하는 계약</b>이라,
     * 나중에 붙는 클라이언트(특히 Flutter 앱)가 여기에 바인딩하면 첨부가 있는 글을 없는 글로 그린다.
     * 채우는 대신 <b>뺀다</b>(근거는 03 §14-2 — 채우면 {@code /api/files} 와 같은 사실의 두 번째 출처가 생긴다).
     */
    @Test
    void communityDto_does_not_expose_always_empty_file_fields() throws Exception {
        mockMvc.perform(get("/api/communities/{communityId}", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.imageUrls").doesNotExist())
                .andExpect(jsonPath("$.data.attachments").doesNotExist());

        mockMvc.perform(get("/api/communities")
                        .param("page", "0")
                        .param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].imageUrls").doesNotExist())
                .andExpect(jsonPath("$.data.content[0].attachments").doesNotExist());
    }
}
