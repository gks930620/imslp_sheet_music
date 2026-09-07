package com.test.test.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.test.test.integration.support.AdminApiTestSupport;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 관리 홈 숫자 카드 (02_API_명세서 §4-1, 01_ERD §4 계산 규칙) — TDD Red.
 * 시드 곡이 같은 DB 에 있으므로 절대값이 아니라 <b>이 테스트가 만든 데이터만큼의 증가분</b>을 검증한다.
 */
class DashboardApiIntegrationTest extends AdminApiTestSupport {

    @Test
    void dashboard_has_all_fields_and_job_slots() throws Exception {
        Tokens admin = loginAdmin();
        JsonNode data = data(adminGet(admin, "/api/admin/dashboard")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true)));

        for (String field : new String[] {"totalWorks", "readyWorks", "preparingWorks", "needsWorkWorks",
                "unknownCopyrightEditions", "monthlyDownloads"}) {
            assertThat(data.path(field).isIntegralNumber()).as(field).isTrue();
            assertThat(data.path(field).asLong()).as(field).isGreaterThanOrEqualTo(0);
        }
        // 값이 없어도 키는 내려온다 (§0-4: null 로 내려준다, 키 생략 안 함)
        assertThat(data.has("latestJob")).isTrue();
        assertThat(data.has("activeJob")).isTrue();
        assertThat(data.path("latestJob").isNull() || data.path("latestJob").has("id")).isTrue();
        assertThat(data.path("activeJob").isNull() || data.path("activeJob").has("status")).isTrue();
    }

    @Test
    void counts_follow_status_and_needs_work_rules_including_hidden_works() throws Exception {
        Tokens admin = loginAdmin();
        JsonNode before = data(adminGet(admin, "/api/admin/dashboard").andExpect(status().isOk()));

        long composerId = createComposer(admin);

        // READY, 보완 없음
        long ready = createWork(admin, composerId);
        long readyEdition = makeReady(admin, ready);

        // PREPARING, 보완 필요(추천 없음) + UNKNOWN 판본 1개(추천 아님)
        long preparing = createWork(admin, composerId);
        createFileEdition(admin, preparing, "UNKNOWN", null);

        // 숨김 + RESTRICTED 추천 — totalWorks 에는 들어가고 ready/preparing 에는 안 들어간다, 보완 없음
        Map<String, Object> hiddenBody = workBody(composerId, "숨긴 곡", "Hidden " + uniq());
        hiddenBody.put("hidden", true);
        long hidden = createWork(admin, hiddenBody);
        setRecommended(admin, hidden, createFileEdition(admin, hidden, "RESTRICTED", "근거"));

        // 다운로드 1회 (공개 API) → 이번 달 다운로드 +1
        mockMvc.perform(get("/api/editions/{id}/download", readyEdition)).andExpect(status().isOk());

        JsonNode after = data(adminGet(admin, "/api/admin/dashboard").andExpect(status().isOk()));

        assertThat(delta(before, after, "totalWorks")).isEqualTo(3);
        assertThat(delta(before, after, "readyWorks")).isEqualTo(1);
        assertThat(delta(before, after, "preparingWorks")).isEqualTo(1);
        assertThat(delta(before, after, "needsWorkWorks")).isEqualTo(1);
        assertThat(delta(before, after, "unknownCopyrightEditions")).isEqualTo(1);
        assertThat(delta(before, after, "monthlyDownloads")).isEqualTo(1);

        // 숨김 곡을 지우면 총계만 준다
        adminDelete(admin, "/api/admin/works/{id}", hidden).andExpect(status().isNoContent());
        JsonNode afterDelete = data(adminGet(admin, "/api/admin/dashboard").andExpect(status().isOk()));
        assertThat(delta(before, afterDelete, "totalWorks")).isEqualTo(2);
        assertThat(delta(before, afterDelete, "readyWorks")).isEqualTo(1);
    }

    private static long delta(JsonNode before, JsonNode after, String field) {
        return after.path(field).asLong() - before.path(field).asLong();
    }
}
