package com.test.test.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.test.test.file.service.OrphanFileCleaner;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * orphan(버려진 임시) 파일 정리 배치 — code-convention §5-3-1 ③ — TDD Red + 가드.
 *
 * <p>스케줄 메서드는 컨트롤러로 도달할 수 없으므로 §6의 "시간 파라미터 주입" 결정적 테스트로 검증한다.
 * {@link OrphanFileCleaner#cleanupOrphans(LocalDateTime)} 에 기준 시각을 주입해,
 * 파일을 실제로 오래되게 만들지 않고도 "유예시간 경과" 상황을 재현한다.
 * (존재 여부는 다운로드 API {@code /api/files/{id}/content} 의 200/404 로 관찰.)
 */
class FileOrphanCleanupTest extends ApiIntegrationTestSupport {

    /** 유예시간 기본값(§5-3-1 예시 24h). 이 값보다 뒤로 now 를 주입하면 삭제 대상이 된다. */
    private static final int GRACE_HOURS = 24;

    @Autowired
    private OrphanFileCleaner orphanFileCleaner;

    // ── RED ────────────────────────────────────────────────────────────
    // 유예시간이 지난 orphan(refId=0)은 삭제돼야 한다.
    @Test
    void old_orphan_draft_is_deleted_after_grace() throws Exception {
        Tokens tokens = loginDefaultUser();
        String name = "orphan-old-" + shortId() + ".png";
        long fileId = uploadDraftAndGetId(tokens, name);

        // 정리 전에는 다운로드 가능
        mockMvc.perform(get("/api/files/{id}/content", fileId)).andExpect(status().isOk());

        // 업로드 시각보다 (유예+1)시간 뒤로 now 주입 → 이 파일은 "오래된 orphan"이 된다
        orphanFileCleaner.cleanupOrphans(LocalDateTime.now().plusHours(GRACE_HOURS + 1));

        mockMvc.perform(get("/api/files/{id}/content", fileId)).andExpect(status().isNotFound());
    }

    // ── GUARD ──────────────────────────────────────────────────────────
    // 유예시간 이내의 orphan(작성 중 초안)은 보호돼야 한다. ("refId=0 전부 삭제" 금지)
    @Test
    void recent_orphan_draft_is_kept_within_grace() throws Exception {
        Tokens tokens = loginDefaultUser();
        String name = "orphan-fresh-" + shortId() + ".png";
        long fileId = uploadDraftAndGetId(tokens, name);

        // 방금 올린 초안 → 유예시간 이내이므로 살아 있어야 한다
        orphanFileCleaner.cleanupOrphans(LocalDateTime.now());

        mockMvc.perform(get("/api/files/{id}/content", fileId)).andExpect(status().isOk());
    }

    // ── GUARD ──────────────────────────────────────────────────────────
    // 글에 연결된 파일(refId != 0)은 오래돼도 orphan 정리 대상이 아니다.
    @Test
    void linked_file_is_never_deleted_by_orphan_cleanup() throws Exception {
        Tokens tokens = loginDefaultUser();
        long postId = createCommunity(tokens);
        String name = "linked-" + shortId() + ".txt";
        long fileId = uploadAndGetId(tokens, String.valueOf(postId), "ATTACHMENT", name,
                MediaType.TEXT_PLAIN_VALUE);

        orphanFileCleaner.cleanupOrphans(LocalDateTime.now().plusHours(GRACE_HOURS + 1));

        mockMvc.perform(get("/api/files/{id}/content", fileId)).andExpect(status().isOk());
    }

    // ===== helpers =====

    private long uploadDraftAndGetId(Tokens tokens, String originalFilename) throws Exception {
        return uploadAndGetId(tokens, "0", "IMAGES", originalFilename, MediaType.IMAGE_PNG_VALUE);
    }

    private long uploadAndGetId(Tokens tokens, String refId, String usage,
                                String originalFilename, String contentType) throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "files", originalFilename, contentType,
                ("bytes-" + originalFilename).getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/files")
                        .file(file)
                        .param("refId", refId)
                        .param("refType", "COMMUNITY")
                        .param("usage", usage)
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokens.accessToken())))
                .andExpect(status().isCreated());

        MockHttpServletRequestBuilder req = get("/api/files")
                .param("refId", refId)
                .param("refType", "COMMUNITY")
                .param("usage", usage);
        MvcResult result = mockMvc.perform(req).andExpect(status().isOk()).andReturn();

        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
        for (JsonNode f : data) {
            if (originalFilename.equals(f.path("originalFileName").asText())) {
                return f.path("fileId").asLong();
            }
        }
        throw new AssertionError("업로드된 파일을 찾을 수 없습니다: " + originalFilename);
    }

    private long createCommunity(Tokens tokens) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/communities")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokens.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "orphan-linked-owner",
                                  "content": "owner post"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").asLong();
    }

    private static String shortId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
