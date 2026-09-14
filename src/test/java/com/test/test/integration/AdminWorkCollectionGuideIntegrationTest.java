package com.test.test.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.test.test.integration.support.AdminApiTestSupport;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * `collectionGuide` 왕복 — 02 §4-7 응답에 필드가 있어야 한다 (2026-09-08 계약 보완, senior-dev).
 *
 * <p><b>왜 계약을 고쳤나 (backend-dev 지적).</b> §4-8 요청 DTO 에는 `collectionGuide` 가 있는데 §4-7 응답에는 없었다.
 * 그런데 PUT 은 <b>전체 교체</b>다 — 관리 화면은 상세 응답으로 폼을 채우고 그 폼을 그대로 되돌려 보낸다.
 * 응답에 필드가 없으면 폼이 값을 들고 있을 수 없어, <b>관리자가 곡을 한 번 저장하는 것만으로 시드가 넣은 38곡의
 * 수록곡 안내가 조용히 지워진다</b>. 지워지면 복구도 안 된다 — 01_ERD §6 백필은 `seed_load(COLLECTION_GUIDE, imslp_url)`
 * 기록이 이미 있어 다시 채우지 않는다. 그리고 그 값 하나가 검색 항목 `scopeNote` 의 `COLLECTION` 판정 근거라(02 §2-2-1),
 * 지워지면 사용자 화면에서 "'강아지 왈츠'가 들어 있는 악보" 줄까지 함께 사라진다.
 *
 * <p>고치는 방향은 <b>응답에 필드를 넣는 것</b>이지 "PUT 에서 빠지면 유지" 가 아니다 — 후자를 택하면 값을 지울 방법이
 * 사라지고, 별칭·작품번호와 다른 예외 규칙이 하나 더 생긴다(전체 교체 계약이 필드마다 달라지면 아무도 못 외운다).
 */
class AdminWorkCollectionGuideIntegrationTest extends AdminApiTestSupport {

    private static final String GUIDE = "이 악보에는 3개 악장이 들어 있어요 — 흔히 아는 느린 선율은 1악장이에요";

    @Test
    @DisplayName("저장한 수록곡 안내가 §4-7 관리 상세와 §3-3 곡 상세에 같은 값으로 온다")
    void savedCollectionGuide_comesBackInAdminDetail() throws Exception {
        Tokens admin = loginAdmin();
        long composerId = createComposer(admin);
        Map<String, Object> body = workBody(composerId, "수록곡안내 곡 " + uniq(), "Collection Guide Work " + uniq());
        body.put("collectionGuide", GUIDE);

        JsonNode created = data(adminPost(admin, "/api/admin/works", body).andExpect(status().isCreated()));
        assertThat(created.path("collectionGuide").asText()).isEqualTo(GUIDE);

        long workId = created.path("id").asLong();
        assertThat(getWork(admin, workId).path("collectionGuide").asText()).isEqualTo(GUIDE);
        publicWorkDetail(workId).andExpect(jsonPath("$.data.collectionGuide").value(GUIDE));
    }

    @Test
    @DisplayName("값이 없는 곡은 null 로 온다 — 필드 자체는 항상 있다")
    void withoutGuide_theFieldIsPresentAndNull() throws Exception {
        Tokens admin = loginAdmin();
        long workId = createWork(admin, createComposer(admin));

        JsonNode detail = getWork(admin, workId);
        assertThat(detail.has("collectionGuide")).isTrue();
        assertThat(detail.path("collectionGuide").isNull()).isTrue();
    }

    @Test
    @DisplayName("상세 응답으로 만든 PUT 본문(= 관리 화면 폼 왕복)이 시드 곡의 수록곡 안내를 지우지 않는다")
    void adminFormRoundTrip_keepsSeededGuide() throws Exception {
        Tokens admin = loginAdmin();
        long workId = findSeedCollectionWorkId();

        JsonNode before = getWork(admin, workId);
        String seeded = before.path("collectionGuide").asText(null);
        assertThat(seeded).as("시드 묶음 곡의 수록곡 안내").isNotBlank();

        // 관리자가 상세 화면에서 제목만 고치고 저장한다 — 폼은 상세 응답에 있는 필드만 들고 있다
        Map<String, Object> form = workSaveBodyFrom(before);
        form.put("titleKo", "월광 소나타(수정) " + uniq());
        JsonNode saved = data(adminPut(admin, "/api/admin/works/{id}", form, workId).andExpect(status().isOk()));

        assertThat(saved.path("collectionGuide").asText(null)).isEqualTo(seeded);
        assertThat(getWork(admin, workId).path("collectionGuide").asText(null)).isEqualTo(seeded);
        // 사용자 화면의 근거도 그대로 남는다 (02 §2-2-1 COLLECTION)
        publicWorkDetail(workId).andExpect(jsonPath("$.data.collectionGuide").value(seeded));
    }

    @Test
    @DisplayName("전체 교체 계약 그대로 — 본문에서 빼면 null 이 된다(지울 수 있어야 한다)")
    void omittingIt_clearsIt() throws Exception {
        Tokens admin = loginAdmin();
        long composerId = createComposer(admin);
        Map<String, Object> body = workBody(composerId, "수록곡안내 삭제곡 " + uniq(), "Collection Guide Clear " + uniq());
        body.put("collectionGuide", GUIDE);
        long workId = createWork(admin, body);

        body.put("collectionGuide", null);
        JsonNode saved = data(adminPut(admin, "/api/admin/works/{id}", body, workId).andExpect(status().isOk()));
        assertThat(saved.path("collectionGuide").isNull()).isTrue();
    }

    // ===== 헬퍼 =====

    /** 시드 묶음 곡(수록곡 안내가 있는 38곡 중 하나) — 검색으로 찾는다. */
    private long findSeedCollectionWorkId() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/works/search").param("q", "강아지 왈츠"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode content = objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("works").path("content");
        List<String> found = new ArrayList<>();
        for (JsonNode item : content) {
            found.add(item.path("titleOriginal").asText());
            if ("Waltzes, Op.64".equals(item.path("titleOriginal").asText())) {
                return item.path("id").asLong();
            }
        }
        throw new AssertionError("시드 묶음 곡을 찾지 못함: " + found);
    }

    private org.springframework.test.web.servlet.ResultActions publicWorkDetail(long workId) throws Exception {
        return mockMvc.perform(get("/api/works/{id}", workId)).andExpect(status().isOk());
    }
}
