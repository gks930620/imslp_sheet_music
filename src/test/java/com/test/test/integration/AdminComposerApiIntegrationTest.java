package com.test.test.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.test.test.integration.support.AdminApiTestSupport;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 관리자 작곡가 API (02_API_명세서 §4-2 ~ §4-5) — TDD Red.
 * 시드(작곡가 25명)가 같은 DB 에 있으므로 고유한 원어 표기를 쓰고, 목록 검증은 내 id 포함 여부로 한다.
 */
class AdminComposerApiIntegrationTest extends AdminApiTestSupport {

    // ===== §4-4 등록 =====

    @Test
    void create_returns_201_with_detail_dto() throws Exception {
        Tokens admin = loginAdmin();
        String id = uniq();
        Map<String, Object> body = json(
                "nameKo", "테스트베토벤" + id,
                "nameOriginal", "Testbeethoven, Ludwig " + id,
                "aliases", List.of("루트비히 판 테스트베토벤", "Testbeethoven"),
                "birthYear", 1770,
                "deathYear", 1827,
                "nationality", "독일",
                "imslpUrl", "https://imslp.org/wiki/Category:Testbeethoven,_Ludwig_van");

        JsonNode data = data(adminPost(admin, "/api/admin/composers", body)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true)));

        assertThat(data.path("id").asLong()).isPositive();
        assertThat(data.path("nameKo").asText()).isEqualTo("테스트베토벤" + id);
        assertThat(data.path("nameOriginal").asText()).isEqualTo("Testbeethoven, Ludwig " + id);
        assertThat(strings(data.path("aliases"))).containsExactly("루트비히 판 테스트베토벤", "Testbeethoven");
        assertThat(data.path("birthYear").asInt()).isEqualTo(1770);
        assertThat(data.path("deathYear").asInt()).isEqualTo(1827);
        assertThat(data.path("nationality").asText()).isEqualTo("독일");
        assertThat(data.path("imslpUrl").asText()).isEqualTo("https://imslp.org/wiki/Category:Testbeethoven,_Ludwig_van");
        assertThat(data.path("workCount").asInt()).isZero();
        assertThat(data.path("createdAt").isTextual()).isTrue();
        assertThat(data.path("updatedAt").isTextual()).isTrue();
    }

    @Test
    void create_validation_errors_report_field() throws Exception {
        Tokens admin = loginAdmin();

        Map<String, Object> blankKo = composerBody("   ", "Blankko, Test " + uniq());
        adminPost(admin, "/api/admin/composers", blankKo)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[?(@.field == 'nameKo')].message").value(org.hamcrest.Matchers.hasItem("한글 표기를 입력해 주세요")));

        adminPost(admin, "/api/admin/composers", composerBody("빈원어", " "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[?(@.field == 'nameOriginal')]").exists());

        Map<String, Object> deathBeforeBirth = composerBody("몰년오류", "Deathfirst, Test " + uniq());
        deathBeforeBirth.put("birthYear", 1850);
        deathBeforeBirth.put("deathYear", 1800);
        adminPost(admin, "/api/admin/composers", deathBeforeBirth)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[?(@.field == 'deathYear')].message").value(org.hamcrest.Matchers.hasItem("몰년이 생년보다 앞서요")));

        Map<String, Object> yearOutOfRange = composerBody("연도범위", "Yearrange, Test " + uniq());
        yearOutOfRange.put("birthYear", 999);
        adminPost(admin, "/api/admin/composers", yearOutOfRange)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[?(@.field == 'birthYear')]").exists());

        Map<String, Object> badUrl = composerBody("주소오류", "Badurl, Test " + uniq());
        badUrl.put("imslpUrl", "https://example.com/wiki/Category:Nope");
        adminPost(admin, "/api/admin/composers", badUrl)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[?(@.field == 'imslpUrl')]").exists());
    }

    @Test
    void create_duplicate_name_original_normalized_returns_409() throws Exception {
        Tokens admin = loginAdmin();
        String id = uniq();
        createComposer(admin, composerBody("중복테스트", "Dupcomposer, Frédéric " + id));

        // 악센트·마침표·공백이 달라도 정규화 값이 같으면 같은 작곡가
        adminPost(admin, "/api/admin/composers", composerBody("중복테스트2", "Dupcomposer, Frederic. " + id))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("DUPLICATE_RESOURCE"))
                .andExpect(jsonPath("$.message").value("이미 등록된 작곡가예요"));
    }

    @Test
    void create_collapses_aliases_that_normalize_equal() throws Exception {
        Tokens admin = loginAdmin();
        Map<String, Object> body = composerBody("별칭중복", "Aliasdup, Test " + uniq());
        body.put("aliases", List.of("쇼팡", "쇼 팡", "쇼팡", "Chopin", "chopin."));

        JsonNode data = data(adminPost(admin, "/api/admin/composers", body).andExpect(status().isCreated()));
        assertThat(strings(data.path("aliases"))).containsExactly("쇼팡", "Chopin");
    }

    // ===== §4-2 목록 / §4-3 상세 =====

    @Test
    void list_filters_by_q_including_alias_and_returns_page() throws Exception {
        Tokens admin = loginAdmin();
        String id = uniq();
        Map<String, Object> body = composerBody("목록테스트" + id, "Listcomposer, Test " + id);
        body.put("aliases", List.of("리스트별칭" + id));
        long composerId = createComposer(admin, body);
        createWork(admin, composerId);

        JsonNode page = data(adminQuery(admin, "/api/admin/composers", "q", "리스트별칭" + id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(20))
                .andExpect(jsonPath("$.data.totalElements").value(1)));
        JsonNode row = page.path("content").get(0);
        assertThat(row.path("id").asLong()).isEqualTo(composerId);
        assertThat(row.path("nameKo").asText()).isEqualTo("목록테스트" + id);
        assertThat(row.path("nameOriginal").asText()).isEqualTo("Listcomposer, Test " + id);
        assertThat(row.path("birthYear").asInt()).isEqualTo(1800);
        assertThat(row.path("deathYear").asInt()).isEqualTo(1850);
        assertThat(row.path("workCount").asInt()).isEqualTo(1);
        assertThat(row.path("updatedAt").isTextual()).isTrue();

        // 원어 표기 부분 일치(정규화: 대소문자·쉼표 무시)
        adminQuery(admin, "/api/admin/composers", "q", "listcomposer test " + id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].id").value(composerId));

        // missingKo=true 는 한글 표기가 없는 작곡가만 — 관리자 등록분은 항상 nameKo 가 있으므로 제외된다
        JsonNode missing = data(adminQuery(admin, "/api/admin/composers", "missingKo", "true").andExpect(status().isOk()));
        assertThat(longs(missing.path("content"), "id")).doesNotContain(composerId);
    }

    @Test
    void detail_returns_dto_and_404_when_absent() throws Exception {
        Tokens admin = loginAdmin();
        String id = uniq();
        Map<String, Object> body = composerBody("상세테스트" + id, "Detailcomposer, Test " + id);
        body.put("aliases", List.of("상세별칭"));
        long composerId = createComposer(admin, body);
        createWork(admin, composerId);
        createWork(admin, composerId);

        adminGet(admin, "/api/admin/composers/{id}", composerId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(composerId))
                .andExpect(jsonPath("$.data.nameKo").value("상세테스트" + id))
                .andExpect(jsonPath("$.data.aliases[0]").value("상세별칭"))
                .andExpect(jsonPath("$.data.nationality").value("테스트국"))
                .andExpect(jsonPath("$.data.imslpUrl").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data.workCount").value(2))
                .andExpect(jsonPath("$.data.createdAt").isString());

        adminGet(admin, "/api/admin/composers/{id}", 99_999_999L)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("NOT_FOUND"));
    }

    // ===== §4-4 수정 =====

    @Test
    void update_replaces_aliases_entirely_and_returns_200() throws Exception {
        Tokens admin = loginAdmin();
        String id = uniq();
        Map<String, Object> body = composerBody("수정전", "Updatecomposer, Test " + id);
        body.put("aliases", List.of("옛별칭", "유지별칭"));
        long composerId = createComposer(admin, body);

        Map<String, Object> update = composerBody("수정후", "Updatecomposer, Test " + id);
        update.put("aliases", List.of("유지별칭", "새별칭"));
        update.put("deathYear", 1860);

        JsonNode data = data(adminPut(admin, "/api/admin/composers/{id}", update, composerId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true)));
        assertThat(data.path("nameKo").asText()).isEqualTo("수정후");
        assertThat(data.path("deathYear").asInt()).isEqualTo(1860);
        assertThat(strings(data.path("aliases"))).containsExactly("유지별칭", "새별칭");

        // 다시 읽어도 같다
        JsonNode reread = data(adminGet(admin, "/api/admin/composers/{id}", composerId).andExpect(status().isOk()));
        assertThat(strings(reread.path("aliases"))).containsExactly("유지별칭", "새별칭");

        adminPut(admin, "/api/admin/composers/{id}", update, 99_999_999L)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("NOT_FOUND"));
    }

    @Test
    void update_to_another_composers_name_original_returns_409() throws Exception {
        Tokens admin = loginAdmin();
        String id = uniq();
        createComposer(admin, composerBody("먼저", "Firstcomposer, Test " + id));
        long second = createComposer(admin, composerBody("나중", "Secondcomposer, Test " + id));

        adminPut(admin, "/api/admin/composers/{id}", composerBody("나중", "firstcomposer test " + id), second)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("DUPLICATE_RESOURCE"));
    }

    // ===== §4-5 삭제 =====

    @Test
    void delete_without_works_returns_204_then_404() throws Exception {
        Tokens admin = loginAdmin();
        long composerId = createComposer(admin);

        adminDelete(admin, "/api/admin/composers/{id}", composerId).andExpect(status().isNoContent());
        adminGet(admin, "/api/admin/composers/{id}", composerId).andExpect(status().isNotFound());
        adminDelete(admin, "/api/admin/composers/{id}", composerId).andExpect(status().isNotFound());
    }

    @Test
    void delete_with_works_returns_400_with_count_in_message() throws Exception {
        Tokens admin = loginAdmin();
        long composerId = createComposer(admin);
        createWork(admin, composerId);
        createWork(admin, composerId);

        adminDelete(admin, "/api/admin/composers/{id}", composerId)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_RULE_VIOLATION"))
                .andExpect(jsonPath("$.message").value("곡 2개가 있어 삭제할 수 없어요"));

        adminGet(admin, "/api/admin/composers/{id}", composerId).andExpect(status().isOk());
    }
}
