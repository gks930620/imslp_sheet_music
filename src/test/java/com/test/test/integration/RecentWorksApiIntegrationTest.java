package com.test.test.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.test.test.integration.support.AdminApiTestSupport;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 최근 본 곡의 "지금 정보" — 02 §3-9. 기획 05 §4-2, 화면정의 01_홈, 인수 조건 8-E 7·8·9·11.
 *
 * <p>브라우저는 <b>곡 id 만</b> 저장하고(03 §23) 표시는 이 문이 매번 답한다. 그래서 이 API 가 지켜야 할 것은
 * 둘이다: <b>요청한 순서 그대로</b>(브라우저가 든 "최근에 본 순" 을 서버가 다시 정하지 않는다),
 * <b>내려간 곡은 조용히 빠진다</b>(눌러서 404 를 만나지 않는다).
 */
class RecentWorksApiIntegrationTest extends AdminApiTestSupport {

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    @DisplayName("요청한 id 순서 그대로 돌려준다 — 서버가 다시 정렬하지 않는다")
    void keepsRequestedOrder() throws Exception {
        Tokens admin = loginAdmin();
        long composerId = createComposer(admin);
        long first = createWork(admin, composerId);
        long second = createWork(admin, composerId);
        long third = createWork(admin, composerId);

        assertThat(recentIds(third + "," + first + "," + second)).containsExactly(third, first, second);
    }

    @Test
    @DisplayName("비로그인도 200 — 최근 본 곡은 로그인과 무관하다 (기획 05 §0-4)")
    void isPublic() throws Exception {
        Tokens admin = loginAdmin();
        long workId = createWork(admin, createComposer(admin));

        mockMvc.perform(get("/api/works/recent").param("ids", String.valueOf(workId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].id").value(workId));
    }

    @Test
    @DisplayName("없는 곡·숨김 곡은 조용히 빠진다 — 오류가 아니다 (8-E 8)")
    void dropsMissingAndHiddenWorks() throws Exception {
        Tokens admin = loginAdmin();
        long composerId = createComposer(admin);
        long visible = createWork(admin, composerId);
        long hidden = createWork(admin, composerId);
        setWorkHidden(admin, hidden, true);

        assertThat(recentIds(hidden + "," + visible + ",99999999")).containsExactly(visible);

        setWorkHidden(admin, hidden, false);
        assertThat(recentIds(hidden + "," + visible))
                .as("숨김이 풀리면 다시 보인다")
                .containsExactly(hidden, visible);
    }

    @Test
    @DisplayName("곡 카드에 필요한 '지금의 정보' 를 담는다 — WorkSummaryDTO 그대로 (8-E 7)")
    void returnsWorkSummary() throws Exception {
        Tokens admin = loginAdmin();
        long workId = createWork(admin, createComposer(admin));

        JsonNode item = recent(String.valueOf(workId)).get(0);
        assertThat(item.path("id").asLong()).isEqualTo(workId);
        assertThat(item.path("titleKo").asText()).isNotBlank();
        assertThat(item.has("composer")).isTrue();
        assertThat(item.path("status").asText()).isEqualTo("PREPARING");
        assertThat(item.path("matchedAlias").isNull())
                .as("검색이 아니므로 별칭 일치 줄은 없다")
                .isTrue();
    }

    @Test
    @DisplayName("다른 구분의 곡은 이 구분의 홈에 나오지 않는다 (기획 05 §5-6)")
    void scopedBySection() throws Exception {
        Tokens admin = loginAdmin();
        long composerId = createComposer(admin);
        long pianoWork = createWork(admin, composerId);
        long violinWork = createWork(admin, composerId);
        moveToSection(violinWork, "VIOLIN");

        String ids = violinWork + "," + pianoWork;
        assertThat(recentIds(ids)).containsExactly(pianoWork);
        assertThat(recentIds(ids, "VIOLIN")).containsExactly(violinWork);
    }

    @Test
    @DisplayName("10개를 넘기면 앞 10개만 쓴다 — 400 이 아니다 (limit·size 상한과 같은 관용)")
    void takesFirstTenOnly() throws Exception {
        Tokens admin = loginAdmin();
        long composerId = createComposer(admin);
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            ids.add(createWork(admin, composerId));
        }

        List<Long> returned = recentIds(ids.stream().map(String::valueOf).collect(Collectors.joining(",")));
        assertThat(returned).hasSize(10);
        assertThat(returned).containsExactlyElementsOf(ids.subList(0, 10));
    }

    @Test
    @DisplayName("중복 id 는 한 번만, 숫자가 아닌 값은 무시 — 브라우저 저장은 오염될 수 있다")
    void ignoresDuplicatesAndGarbage() throws Exception {
        Tokens admin = loginAdmin();
        long workId = createWork(admin, createComposer(admin));

        assertThat(recentIds(workId + ",abc,," + workId)).containsExactly(workId);
    }

    @Test
    @DisplayName("전부 걸러지거나 ids 가 비면 200 + 빈 배열 — 화면이 영역 자체를 걷는다 (8-E 11)")
    void emptyResultIsOkNotError() throws Exception {
        assertThat(recent("99999999")).isEmpty();
        assertThat(recent("")).isEmpty();
    }

    @Test
    @DisplayName("ids 파라미터 자체가 없으면 400 MISSING_PARAMETER (§0-2 표준)")
    void missingIdsParam_is400() throws Exception {
        mockMvc.perform(get("/api/works/recent"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("MISSING_PARAMETER"));
    }

    @Test
    @DisplayName("정의되지 않은 section 은 400 (§0-7)")
    void unknownSection_is400() throws Exception {
        mockMvc.perform(get("/api/works/recent").param("ids", "1").param("section", "CELLO"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_RULE_VIOLATION"));
    }

    // ===== 헬퍼 =====

    private JsonNode recent(String ids) throws Exception {
        return recent(ids, null);
    }

    private JsonNode recent(String ids, String section) throws Exception {
        var request = get("/api/works/recent").param("ids", ids);
        if (section != null) {
            request = request.param("section", section);
        }
        MvcResult result = mockMvc.perform(request).andExpect(status().isOk()).andReturn();
        return data(result);
    }

    private List<Long> recentIds(String ids) throws Exception {
        return recentIds(ids, null);
    }

    private List<Long> recentIds(String ids, String section) throws Exception {
        List<Long> list = new ArrayList<>();
        recent(ids, section).forEach(node -> list.add(node.path("id").asLong()));
        return list;
    }

    /** 02 §0-7 — 1차에 구분을 바꾸는 API 가 없으므로 given 만 컬럼에 직접 쓴다(SectionScopeIntegrationTest 와 같다). */
    private void moveToSection(long workId, String section) {
        entityManager.flush();
        entityManager.createNativeQuery("UPDATE work SET section = :section WHERE id = :id")
                .setParameter("section", section)
                .setParameter("id", workId)
                .executeUpdate();
        entityManager.clear();
    }
}
