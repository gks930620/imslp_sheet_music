package com.test.test.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.test.test.integration.support.CrawlTestSupport;
import com.test.test.integration.support.FakeImslpClient;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code NOT_PIANO_SOLO} 로 숨긴 곡은 <b>지워지지도, 다른 구분에 자동 배정되지도 않는다</b>
 * — 기획 04 §2(1·2·5), §9 8-7. 계약: 02 §0-7 · 01_ERD §3-3.
 *
 * <p>숨김 사유는 <b>부정형</b>("피아노가 아니다")이라 그 안에 바이올린 소나타·관현악 총보·성악·실내악이
 * 한 덩어리로 섞여 있다. 구분을 열 때 이 곡들이 자동으로 "바이올린" 이 되면 사용자가 바이올린 목록에서
 * 총보를 보게 된다 — 그래서 <b>1차에는 값이 PIANO 그대로여야 하고 숨김도 그대로여야 한다.</b>
 *
 * <p>{@code section} 은 관리 API 응답에도 없으므로(§0-7) 컬럼을 직접 읽는다. 이 한 줄이 이 테스트의 전부다.
 */
class HiddenWorkSectionIntegrationTest extends CrawlTestSupport {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("수집이 숨긴 비-피아노 곡: section 은 PIANO 그대로이고, 곡은 지워지지 않고 숨김으로 남는다")
    void crawlerHiddenWork_staysPianoAndHidden() throws Exception {
        Tokens admin = loginAdmin();

        long jobId = startJob(admin, false, FakeImslpClient.SYMPHONY5_URL).path("id").asLong();
        JsonNode item = item(awaitJobStatus(admin, jobId, "COMPLETED"), 1);
        assertThat(item.path("status").asText()).isEqualTo("HIDDEN");
        long workId = item.path("workId").asLong();

        // 1) 지우지 않는다 — 관리 화면에는 그대로 있다 (기획 04 §2-1)
        JsonNode work = getWork(admin, workId);
        assertThat(work.path("hidden").asBoolean()).isTrue();
        assertThat(work.path("hiddenReason").asText()).isEqualTo("NOT_PIANO_SOLO");

        // 2) 자동으로 다른 구분이 되지 않는다 (기획 04 §2-2)
        assertThat(sectionOf(workId)).isEqualTo("PIANO");
    }

    @Test
    @DisplayName("그 곡은 어느 구분의 사용자 화면에도 나오지 않는다 (숨김이 구분보다 먼저다)")
    void crawlerHiddenWork_isInNoSectionForUsers() throws Exception {
        Tokens admin = loginAdmin();

        long jobId = startJob(admin, false, FakeImslpClient.SYMPHONY5_URL).path("id").asLong();
        JsonNode item = item(awaitJobStatus(admin, jobId, "COMPLETED"), 1);
        long workId = item.path("workId").asLong();

        for (String section : List.of("PIANO", "VIOLIN", "ORCHESTRA")) {
            assertThat(searchIds("Symphony", section)).as("section=%s", section).doesNotContain(workId);
        }
        mockMvc.perform(get("/api/works/{id}", workId)).andExpect(status().isNotFound());
    }

    private String sectionOf(long workId) {
        return jdbc.queryForObject("SELECT section FROM work WHERE id = ?", String.class, workId);
    }

    private List<Long> searchIds(String q, String section) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/works/search").param("q", q).param("section", section))
                .andExpect(status().isOk())
                .andReturn();
        List<Long> ids = new ArrayList<>();
        JsonNode content = objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("works").path("content");
        for (JsonNode node : content) {
            ids.add(node.path("id").asLong());
        }
        return ids;
    }
}
