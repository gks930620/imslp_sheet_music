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
 * 저작권 판정 대기함 (02_API_명세서 §5-8 ~ §5-10) — TDD Red.
 * 시드 곡에는 판본이 없으므로 대기함에는 이 테스트가 만든 판본만 들어온다(트랜잭션 롤백).
 */
class CopyrightInboxApiIntegrationTest extends AdminApiTestSupport {

    // ===== §5-8 대기함 =====

    @Test
    void pending_lists_unknown_editions_sorted_by_composer_death_year_nulls_last() throws Exception {
        Tokens admin = loginAdmin();
        String id = uniq();

        Map<String, Object> chopin = composerBody("테스트쇼팽" + id, "Testchopin, Frédéric " + id);
        chopin.put("birthYear", 1810);
        chopin.put("deathYear", 1849);
        long chopinId = createComposer(admin, chopin);

        Map<String, Object> beethoven = composerBody("테스트베토벤" + id, "Testbeethoven, Ludwig " + id);
        beethoven.put("birthYear", 1770);
        beethoven.put("deathYear", 1827);
        long beethovenId = createComposer(admin, beethoven);

        Map<String, Object> unknownDeath = composerBody("테스트생존" + id, "Testliving, Anon " + id);
        unknownDeath.put("birthYear", 1950);
        unknownDeath.put("deathYear", null);
        long livingId = createComposer(admin, unknownDeath);

        Map<String, Object> nocturnes = workBody(chopinId, null, "Nocturnes, Op.9 " + id);
        nocturnes.put("titleKo", null);
        long nocturnesId = createWork(admin, nocturnes);
        long beethovenWorkId = createWork(admin, workBody(beethovenId, "테스트 소나타", "Test Sonata " + id));
        long livingWorkId = createWork(admin, workBody(livingId, "테스트 소품", "Test Piece " + id));

        Map<String, Object> nocturneEdition = editionBody(null, null, null, "UNKNOWN", null);
        nocturneEdition.put("editor", "Ignacy Paderewski");
        nocturneEdition.put("publisher", "Warsaw: Instytut Fryderyka Chopina, 1949.");
        nocturneEdition.put("publishYear", 1949);
        nocturneEdition.put("imslpCopyrightText", "Public Domain");
        nocturneEdition.put("imslpFileUrl", "https://imslp.org/wiki/Special:ImagefromIndex/00001");
        long nocturneEditionId = createEdition(admin, nocturnesId, nocturneEdition);
        long beethovenEditionId = createFileEdition(admin, beethovenWorkId, "UNKNOWN", null);
        long livingEditionId = createInfoEdition(admin, livingWorkId);
        // 판정된 판본은 대기함에 없다
        long judged = createFileEdition(admin, beethovenWorkId, "FREE", "근거");

        JsonNode data = data(adminGet(admin, "/api/admin/copyright/pending")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.unfilteredTotal").value(3))
                .andExpect(jsonPath("$.data.editions.content").isArray())
                .andExpect(jsonPath("$.data.editions.totalElements").value(3)));

        JsonNode rows = data.path("editions").path("content");
        assertThat(longs(rows, "editionId")).containsExactly(beethovenEditionId, nocturneEditionId, livingEditionId);
        assertThat(longs(rows, "editionId")).doesNotContain(judged);

        JsonNode nocturneRow = rows.get(1);
        assertThat(nocturneRow.path("work").path("id").asLong()).isEqualTo(nocturnesId);
        assertThat(nocturneRow.path("work").path("titleKo").isNull()).isTrue();
        assertThat(nocturneRow.path("work").path("titleOriginal").asText()).isEqualTo("Nocturnes, Op.9 " + id);
        assertThat(nocturneRow.path("composer").path("id").asLong()).isEqualTo(chopinId);
        assertThat(nocturneRow.path("composer").path("nameKo").asText()).isEqualTo("테스트쇼팽" + id);
        assertThat(nocturneRow.path("composer").path("nameOriginal").asText()).isEqualTo("Testchopin, Frédéric " + id);
        assertThat(nocturneRow.path("composer").path("deathYear").asInt()).isEqualTo(1849);
        assertThat(nocturneRow.path("kind").asText()).isEqualTo("COMPLETE_SCORE");
        assertThat(nocturneRow.path("scope").asText()).isEqualTo("COMPLETE");
        assertThat(nocturneRow.path("movementNumber").isNull()).isTrue();
        assertThat(nocturneRow.path("editor").asText()).isEqualTo("Ignacy Paderewski");
        assertThat(nocturneRow.path("arranger").isNull()).isTrue();
        assertThat(nocturneRow.path("publisher").asText()).isEqualTo("Warsaw: Instytut Fryderyka Chopina, 1949.");
        assertThat(nocturneRow.path("publishYear").asInt()).isEqualTo(1949);
        assertThat(nocturneRow.path("imslpCopyrightText").asText()).isEqualTo("Public Domain");
        assertThat(nocturneRow.path("imslpFileUrl").asText()).isEqualTo("https://imslp.org/wiki/Special:ImagefromIndex/00001");
        assertThat(nocturneRow.path("hasFile").asBoolean()).isFalse();
        assertThat(rows.get(0).path("hasFile").asBoolean()).isTrue();
        assertThat(rows.get(2).path("composer").path("deathYear").isNull()).isTrue();

        // composerId 필터 / q(제목·작곡가 부분 일치) 필터 — unfilteredTotal 은 전체 대기 수 그대로
        JsonNode byComposer = data(adminQuery(admin, "/api/admin/copyright/pending", "composerId", String.valueOf(chopinId)).andExpect(status().isOk()));
        assertThat(longs(byComposer.path("editions").path("content"), "editionId")).containsExactly(nocturneEditionId);
        assertThat(byComposer.path("unfilteredTotal").asInt()).isEqualTo(3);

        JsonNode byTitle = data(adminQuery(admin, "/api/admin/copyright/pending", "q", "nocturnes op9 " + id).andExpect(status().isOk()));
        assertThat(longs(byTitle.path("editions").path("content"), "editionId")).containsExactly(nocturneEditionId);

        JsonNode byComposerName = data(adminQuery(admin, "/api/admin/copyright/pending", "q", "테스트베토벤" + id).andExpect(status().isOk()));
        assertThat(longs(byComposerName.path("editions").path("content"), "editionId")).containsExactly(beethovenEditionId);
    }

    // ===== §5-9 단건 판정 =====

    @Test
    void judge_single_edition_updates_status_and_removes_from_inbox() throws Exception {
        Tokens admin = loginAdmin();
        long workId = createWork(admin, createComposer(admin));
        long editionId = createFileEdition(admin, workId, "UNKNOWN", null);
        setRecommended(admin, workId, editionId);

        JsonNode data = data(adminPut(admin, "/api/admin/editions/{id}/copyright",
                json("koreaCopyright", "FREE", "copyrightNote", "작곡가 1849 사망, 편집자 1941 사망 → 경과"), editionId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true)));
        assertThat(data.path("id").asLong()).isEqualTo(editionId);
        assertThat(data.path("koreaCopyright").asText()).isEqualTo("FREE");
        assertThat(data.path("copyrightNote").asText()).isEqualTo("작곡가 1849 사망, 편집자 1941 사망 → 경과");
        assertThat(data.path("copyrightJudgedAt").isTextual()).isTrue();
        assertThat(data.path("copyrightJudgedBy").asText()).isEqualTo(ADMIN_USERNAME);
        assertThat(data.path("downloadable").asBoolean()).isTrue();
        assertThat(data.path("workStatus").asText()).isEqualTo("READY");   // 추천 판본이 FREE → 다운로드가 열림

        JsonNode pending = data(adminGet(admin, "/api/admin/copyright/pending").andExpect(status().isOk()));
        assertThat(longs(pending.path("editions").path("content"), "editionId")).doesNotContain(editionId);

        // RESTRICTED 로 바꾸면 닫힌다
        JsonNode restricted = data(adminPut(admin, "/api/admin/editions/{id}/copyright",
                json("koreaCopyright", "RESTRICTED", "copyrightNote", "편집자 1990 사망"), editionId).andExpect(status().isOk()));
        assertThat(restricted.path("workStatus").asText()).isEqualTo("RESTRICTED");
        assertThat(restricted.path("downloadable").asBoolean()).isFalse();

        // UNKNOWN 으로 되돌리기는 메모 없이 허용
        JsonNode back = data(adminPut(admin, "/api/admin/editions/{id}/copyright",
                json("koreaCopyright", "UNKNOWN", "copyrightNote", null), editionId).andExpect(status().isOk()));
        assertThat(back.path("koreaCopyright").asText()).isEqualTo("UNKNOWN");
        assertThat(back.path("workStatus").asText()).isEqualTo("UNKNOWN");
    }

    @Test
    void judge_requires_note_for_free_and_restricted_and_404_for_unknown_edition() throws Exception {
        Tokens admin = loginAdmin();
        long workId = createWork(admin, createComposer(admin));
        long editionId = createInfoEdition(admin, workId);

        adminPut(admin, "/api/admin/editions/{id}/copyright", json("koreaCopyright", "FREE", "copyrightNote", " "), editionId)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[?(@.field == 'copyrightNote')].message").value(org.hamcrest.Matchers.hasItem("판정 근거를 적어 주세요")));

        adminPut(admin, "/api/admin/editions/{id}/copyright", json("koreaCopyright", "RESTRICTED"), editionId)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field == 'copyrightNote')]").exists());

        adminPut(admin, "/api/admin/editions/{id}/copyright", json("koreaCopyright", null), editionId)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field == 'koreaCopyright')]").exists());

        adminPut(admin, "/api/admin/editions/{id}/copyright", json("koreaCopyright", "FREE", "copyrightNote", "근거"), 99_999_999L)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("NOT_FOUND"));
    }

    // ===== §5-10 일괄 판정 =====

    @Test
    void bulk_judgement_is_partial_and_reports_failures_per_edition() throws Exception {
        Tokens admin = loginAdmin();
        long workId = createWork(admin, createComposer(admin));
        long e1 = createInfoEdition(admin, workId);
        long e2 = createInfoEdition(admin, workId);
        long missing = 99_999_999L;

        JsonNode data = data(adminPost(admin, "/api/admin/editions/copyright/bulk",
                json("editionIds", List.of(e1, e2, missing), "koreaCopyright", "FREE", "copyrightNote", "일괄 근거"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true)));

        assertThat(strings(data.path("succeeded")).stream().map(Long::valueOf)).containsExactly(e1, e2);
        assertThat(data.path("failed")).hasSize(1);
        assertThat(data.path("failed").get(0).path("editionId").asLong()).isEqualTo(missing);
        assertThat(data.path("failed").get(0).path("reason").asText()).isEqualTo("NOT_FOUND");

        // 성공한 것은 실제로 저장됐다
        JsonNode judged = getEdition(admin, e1);
        assertThat(judged.path("koreaCopyright").asText()).isEqualTo("FREE");
        assertThat(judged.path("copyrightNote").asText()).isEqualTo("일괄 근거");
        assertThat(judged.path("copyrightJudgedBy").asText()).isEqualTo(ADMIN_USERNAME);
        assertThat(getEdition(admin, e2).path("koreaCopyright").asText()).isEqualTo("FREE");

        JsonNode pending = data(adminGet(admin, "/api/admin/copyright/pending").andExpect(status().isOk()));
        assertThat(longs(pending.path("editions").path("content"), "editionId")).doesNotContain(e1, e2);
    }

    @Test
    void bulk_validation_requires_ids_and_note() throws Exception {
        Tokens admin = loginAdmin();
        long workId = createWork(admin, createComposer(admin));
        long e1 = createInfoEdition(admin, workId);

        adminPost(admin, "/api/admin/editions/copyright/bulk",
                json("editionIds", List.of(), "koreaCopyright", "FREE", "copyrightNote", "근거"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[?(@.field == 'editionIds')]").exists());

        adminPost(admin, "/api/admin/editions/copyright/bulk",
                json("editionIds", List.of(e1), "koreaCopyright", "RESTRICTED", "copyrightNote", ""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[?(@.field == 'copyrightNote')]").exists());

        // 아무것도 저장되지 않았다
        assertThat(getEdition(admin, e1).path("koreaCopyright").asText()).isEqualTo("UNKNOWN");
    }
}
