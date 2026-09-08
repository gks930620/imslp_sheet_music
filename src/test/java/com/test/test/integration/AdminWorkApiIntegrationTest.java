package com.test.test.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.test.test.integration.support.AdminApiTestSupport;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 관리자 곡 API (02_API_명세서 §4-6 ~ §4-10, 01_ERD §4 상태·보완 계산) — TDD Red.
 * 시드(곡 50)가 같은 DB 에 있으므로 목록 검증은 {@code composerId} 필터로 내 작곡가 곡만 본다.
 */
class AdminWorkApiIntegrationTest extends AdminApiTestSupport {

    private static final String MOONLIGHT_URL = "https://imslp.org/wiki/Test_Sonata_No.14,_Op.27_No.2_(Testcomposer,_%s)";

    // ===== §4-8 등록 + §4-7 상세 =====

    @Test
    void create_returns_201_with_admin_detail_dto_and_computed_fields() throws Exception {
        Tokens admin = loginAdmin();
        String id = uniq();
        long composerId = createComposer(admin, composerBody("테스트작곡가" + id, "Testcomposer, " + id));
        Map<String, Object> body = json(
                "composerId", composerId,
                "titleKo", "월광 소나타 " + id,
                "titleOriginal", "Piano Sonata No.14, Op.27 No.2 " + id,
                "catalogNumbers", List.of("Op.27 No.2"),
                "aliases", List.of("월광" + id, "Moonlight Sonata " + id),
                "level", "INTERMEDIATE",
                "compositionYear", "1801",
                "musicalKey", "C-sharp minor",
                "movements", "3 movements",
                "movementPageGuide", "1악장 1쪽 · 2악장 6쪽",
                "imslpUrl", MOONLIGHT_URL.formatted(id),
                "hidden", false);

        JsonNode data = data(adminPost(admin, "/api/admin/works", body)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true)));

        assertThat(data.path("id").asLong()).isPositive();
        assertThat(data.path("titleKo").asText()).isEqualTo("월광 소나타 " + id);
        assertThat(data.path("titleOriginal").asText()).isEqualTo("Piano Sonata No.14, Op.27 No.2 " + id);
        assertThat(data.path("composer").path("id").asLong()).isEqualTo(composerId);
        assertThat(data.path("composer").path("nameKo").asText()).isEqualTo("테스트작곡가" + id);
        assertThat(data.path("composer").path("nameOriginal").asText()).isEqualTo("Testcomposer, " + id);
        assertThat(data.path("composer").path("deathYear").asInt()).isEqualTo(1850);
        assertThat(data.path("composer").path("nameKoMissing").asBoolean()).isFalse();
        assertThat(strings(data.path("catalogNumbers"))).containsExactly("Op.27 No.2");
        assertThat(strings(data.path("aliases"))).containsExactly("월광" + id, "Moonlight Sonata " + id);
        assertThat(data.path("level").asText()).isEqualTo("INTERMEDIATE");
        assertThat(data.path("compositionYear").asText()).isEqualTo("1801");
        assertThat(data.path("musicalKey").asText()).isEqualTo("C-sharp minor");
        assertThat(data.path("movements").asText()).isEqualTo("3 movements");
        assertThat(data.path("movementPageGuide").asText()).isEqualTo("1악장 1쪽 · 2악장 6쪽");
        assertThat(data.path("imslpUrl").asText()).isEqualTo(MOONLIGHT_URL.formatted(id));
        assertThat(data.path("hidden").asBoolean()).isFalse();
        assertThat(data.path("hiddenReason").isNull()).isTrue();
        // 판본 없음 → PREPARING, 추천 판본만 빠짐
        assertThat(data.path("status").asText()).isEqualTo("PREPARING");
        assertThat(data.path("needsWork").asBoolean()).isTrue();
        assertThat(strings(data.path("missing"))).containsExactly("RECOMMENDED_EDITION");
        assertThat(data.path("recommendedEditionId").isNull()).isTrue();
        assertThat(data.path("candidateEditionId").isNull()).isTrue();
        assertThat(data.path("downloadCount").asLong()).isZero();
        assertThat(data.path("hasDownloadHistory").asBoolean()).isFalse();
        assertThat(data.path("editions").isArray()).isTrue();
        assertThat(data.path("editions")).isEmpty();
        assertThat(data.path("createdAt").isTextual()).isTrue();
        assertThat(data.path("updatedAt").isTextual()).isTrue();
    }

    @Test
    void missing_lists_every_empty_field_and_blank_title_ko_is_saved_as_null() throws Exception {
        Tokens admin = loginAdmin();
        long composerId = createComposer(admin);
        Map<String, Object> body = workBody(composerId, "   ", "Bare Piece " + uniq());
        body.put("aliases", List.of());
        body.put("level", null);

        JsonNode data = data(adminPost(admin, "/api/admin/works", body).andExpect(status().isCreated()));
        assertThat(data.path("titleKo").isNull()).isTrue();
        assertThat(data.path("level").isNull()).isTrue();
        assertThat(data.path("needsWork").asBoolean()).isTrue();
        assertThat(strings(data.path("missing"))).containsExactlyInAnyOrder("TITLE_KO", "ALIAS", "LEVEL", "RECOMMENDED_EDITION");
    }

    @Test
    void needs_work_clears_when_all_fields_filled_and_recommended_set() throws Exception {
        Tokens admin = loginAdmin();
        long composerId = createComposer(admin);
        long workId = createWork(admin, composerId);
        long editionId = makeReady(admin, workId);

        JsonNode data = getWork(admin, workId);
        assertThat(data.path("status").asText()).isEqualTo("READY");
        assertThat(data.path("needsWork").asBoolean()).isFalse();
        assertThat(data.path("missing")).isEmpty();
        assertThat(data.path("recommendedEditionId").asLong()).isEqualTo(editionId);
        assertThat(data.path("candidateEditionId").isNull()).isTrue();   // 추천이 있으면 후보 표시 안 함
        assertThat(data.path("editions").get(0).path("id").asLong()).isEqualTo(editionId);
        assertThat(data.path("editions").get(0).path("isRecommended").asBoolean()).isTrue();
    }

    @Test
    void create_validation_and_404_for_unknown_composer() throws Exception {
        Tokens admin = loginAdmin();
        long composerId = createComposer(admin);

        Map<String, Object> noComposer = workBody(composerId, "제목", "Title " + uniq());
        noComposer.put("composerId", null);
        adminPost(admin, "/api/admin/works", noComposer)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[?(@.field == 'composerId')]").exists());

        adminPost(admin, "/api/admin/works", workBody(99_999_999L, "제목", "Title " + uniq()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("NOT_FOUND"));

        adminPost(admin, "/api/admin/works", workBody(composerId, "제목", "  "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[?(@.field == 'titleOriginal')].message").value(org.hamcrest.Matchers.hasItem("원어 제목을 입력해 주세요")));

        Map<String, Object> badUrl = workBody(composerId, "제목", "Title " + uniq());
        badUrl.put("imslpUrl", "https://example.com/wiki/Nope");
        adminPost(admin, "/api/admin/works", badUrl)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[?(@.field == 'imslpUrl')]").exists());
    }

    @Test
    void create_with_same_imslp_url_normalized_returns_409() throws Exception {
        Tokens admin = loginAdmin();
        long composerId = createComposer(admin);
        String id = uniq();
        Map<String, Object> first = workBody(composerId, "첫 곡", "First " + id);
        first.put("imslpUrl", "https://imslp.org/wiki/Für_Test,_WoO_" + id + "_(Testcomposer,_Ludwig)");
        createWork(admin, first);

        // 퍼센트 인코딩·www·앵커가 달라도 정규 형태가 같으면 같은 곡
        Map<String, Object> second = workBody(composerId, "둘째 곡", "Second " + id);
        second.put("imslpUrl", "https://www.imslp.org/wiki/F%C3%BCr_Test,_WoO_" + id + "_(Testcomposer,_Ludwig)#tabScore1");
        adminPost(admin, "/api/admin/works", second)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("DUPLICATE_RESOURCE"));
    }

    @Test
    void create_collapses_duplicate_aliases_and_catalog_numbers() throws Exception {
        Tokens admin = loginAdmin();
        long composerId = createComposer(admin);
        Map<String, Object> body = workBody(composerId, "중복 별칭", "Dup Alias " + uniq());
        body.put("aliases", List.of("녹턴", "녹 턴", "Nocturne", "nocturne"));
        body.put("catalogNumbers", List.of("Op.9 No.2", "op 9 no 2", "BWV 846"));

        JsonNode data = data(adminPost(admin, "/api/admin/works", body).andExpect(status().isCreated()));
        assertThat(strings(data.path("aliases"))).containsExactly("녹턴", "Nocturne");
        assertThat(strings(data.path("catalogNumbers"))).containsExactly("Op.9 No.2", "BWV 846");
    }

    // ===== §4-8 수정 =====

    @Test
    void update_replaces_aliases_and_catalogs_and_toggles_hidden() throws Exception {
        Tokens admin = loginAdmin();
        long composerId = createComposer(admin);
        Map<String, Object> body = workBody(composerId, "수정 전", "Before " + uniq());
        body.put("aliases", List.of("옛별칭", "유지별칭"));
        body.put("catalogNumbers", List.of("Op.1", "Op.2"));
        long workId = createWork(admin, body);

        Map<String, Object> update = workBody(composerId, "수정 후", "After " + uniq());
        update.put("aliases", List.of("유지별칭", "새별칭"));
        update.put("catalogNumbers", List.of("Op.2"));
        update.put("level", "ADVANCED");
        update.put("hidden", true);

        JsonNode data = data(adminPut(admin, "/api/admin/works/{id}", update, workId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true)));
        assertThat(data.path("titleKo").asText()).isEqualTo("수정 후");
        assertThat(data.path("level").asText()).isEqualTo("ADVANCED");
        assertThat(strings(data.path("aliases"))).containsExactly("유지별칭", "새별칭");
        assertThat(strings(data.path("catalogNumbers"))).containsExactly("Op.2");
        assertThat(data.path("hidden").asBoolean()).isTrue();
        assertThat(data.path("hiddenReason").isNull()).isTrue();   // 관리자 숨김은 사유 없음

        update.put("hidden", false);
        JsonNode shown = data(adminPut(admin, "/api/admin/works/{id}", update, workId).andExpect(status().isOk()));
        assertThat(shown.path("hidden").asBoolean()).isFalse();
        assertThat(shown.path("hiddenReason").isNull()).isTrue();

        adminPut(admin, "/api/admin/works/{id}", update, 99_999_999L)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("NOT_FOUND"));
    }

    // ===== §4-6 목록 =====

    @Test
    void list_filters_by_status_six_kinds_and_composer() throws Exception {
        Tokens admin = loginAdmin();
        long composerId = createComposer(admin);

        long ready = createWork(admin, composerId);
        makeReady(admin, ready);

        long preparing = createWork(admin, composerId);   // 판본 없음

        long restricted = createWork(admin, composerId);
        setRecommended(admin, restricted, createFileEdition(admin, restricted, "RESTRICTED", "편집자 사후 70년 미경과"));

        long unknown = createWork(admin, composerId);
        setRecommended(admin, unknown, createFileEdition(admin, unknown, "UNKNOWN", null));

        Map<String, Object> hiddenBody = workBody(composerId, "숨긴 곡", "Hidden " + uniq());
        hiddenBody.put("hidden", true);
        long hidden = createWork(admin, hiddenBody);

        Map<String, Object> noLevelBody = workBody(composerId, "난이도 미정", "No Level " + uniq());
        noLevelBody.put("level", null);
        long noLevel = createWork(admin, noLevelBody);
        makeReady(admin, noLevel);   // READY 이지만 난이도가 비어 보완 필요

        assertListIds(admin, composerId, "READY", List.of(ready, noLevel));
        assertListIds(admin, composerId, "PREPARING", List.of(preparing, hidden));
        assertListIds(admin, composerId, "RESTRICTED", List.of(restricted));
        assertListIds(admin, composerId, "UNKNOWN", List.of(unknown));
        assertListIds(admin, composerId, "HIDDEN", List.of(hidden));
        // 추천 판본의 판정이 확인 중인 곡도 보완 필요다 (01_ERD §4, 기획 §11-1 — 2026-09-08 개정)
        assertListIds(admin, composerId, "NEEDS_WORK", List.of(preparing, hidden, noLevel, unknown));

        // 필터 없음 = 숨김 포함 전부, updated_at DESC
        JsonNode all = data(adminQuery(admin, "/api/admin/works", "composerId", String.valueOf(composerId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unfilteredTotal").isNumber())
                .andExpect(jsonPath("$.data.works.content").isArray())
                .andExpect(jsonPath("$.data.works.totalElements").value(6)));
        JsonNode first = all.path("works").path("content").get(0);
        assertThat(first.path("id").asLong()).isEqualTo(noLevel);
        assertThat(first.path("composer").path("id").asLong()).isEqualTo(composerId);
        assertThat(first.path("editionCount").asInt()).isEqualTo(1);
        assertThat(first.path("hasRecommended").asBoolean()).isTrue();
        assertThat(first.path("status").asText()).isEqualTo("READY");
        assertThat(first.path("needsWork").asBoolean()).isTrue();
        assertThat(first.path("hidden").asBoolean()).isFalse();
        assertThat(first.path("level").isNull()).isTrue();
        assertThat(first.path("updatedAt").isTextual()).isTrue();

        // level=NONE 은 난이도 미정만
        JsonNode none = data(adminQuery(admin, "/api/admin/works", "composerId", String.valueOf(composerId), "level", "NONE").andExpect(status().isOk()));
        assertThat(longs(none.path("works").path("content"), "id")).containsExactly(noLevel);

        JsonNode intermediate = data(adminQuery(admin, "/api/admin/works", "composerId", String.valueOf(composerId), "level", "INTERMEDIATE").andExpect(status().isOk()));
        assertThat(longs(intermediate.path("works").path("content"), "id"))
                .containsExactlyInAnyOrder(ready, preparing, restricted, unknown, hidden);

        adminQuery(admin, "/api/admin/works", "status", "BOGUS")
                .andExpect(status().isBadRequest());
    }

    @Test
    void list_q_matches_alias_and_includes_hidden_work() throws Exception {
        Tokens admin = loginAdmin();
        long composerId = createComposer(admin);
        String alias = "관리검색별칭" + uniq();
        Map<String, Object> body = workBody(composerId, "숨김 검색", "Hidden Search " + uniq());
        body.put("aliases", List.of(alias));
        body.put("hidden", true);
        long workId = createWork(admin, body);

        JsonNode page = data(adminQuery(admin, "/api/admin/works", "q", alias)
                .andExpect(status().isOk()));
        assertThat(longs(page.path("works").path("content"), "id")).containsExactly(workId);
        assertThat(page.path("works").path("content").get(0).path("hidden").asBoolean()).isTrue();
    }

    // ===== §4-10 별칭 겹침 =====

    @Test
    void alias_overlap_counts_other_works_with_same_normalized_alias() throws Exception {
        Tokens admin = loginAdmin();
        long composerId = createComposer(admin);
        String alias = "겹침별칭" + uniq();

        Map<String, Object> a = workBody(composerId, "곡 A", "Overlap A " + uniq());
        a.put("aliases", List.of(alias));
        long workA = createWork(admin, a);
        Map<String, Object> b = workBody(composerId, "곡 B", "Overlap B " + uniq());
        b.put("aliases", List.of(alias.toUpperCase() + " "));   // 정규화하면 같음
        createWork(admin, b);

        adminQuery(admin, "/api/admin/works/aliases/overlap", "alias", alias, "excludeWorkId", String.valueOf(workA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.alias").value(alias))
                .andExpect(jsonPath("$.data.overlapCount").value(1));

        adminQuery(admin, "/api/admin/works/aliases/overlap", "alias", alias)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.overlapCount").value(2));

        adminQuery(admin, "/api/admin/works/aliases/overlap", "alias", "아무도안쓰는별칭" + uniq())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.overlapCount").value(0));

        adminGet(admin, "/api/admin/works/aliases/overlap")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("MISSING_PARAMETER"));
    }

    // ===== §4-9 삭제 =====

    @Test
    void delete_removes_work_with_editions_and_files() throws Exception {
        Tokens admin = loginAdmin();
        long composerId = createComposer(admin);
        long workId = createWork(admin, composerId);
        JsonNode upload = uploadSamplePdf(admin);
        long fileId = upload.path("fileId").asLong();
        long previewFileId = upload.path("previewFileId").asLong();
        long editionId = createEdition(admin, workId, editionBody(fileId, previewFileId, 2, "FREE", "근거"));
        setRecommended(admin, workId, editionId);
        long infoEditionId = createInfoEdition(admin, workId);

        // 삭제 전: 파일이 살아 있다. 판본 파일은 공용 파일 API 로 관찰하지 않는다
        // (02 §3-4 (2026-09-07): /api/files/** 는 EDITION 을 다루지 않는다) — 정식 다운로드 + files 행으로 본다.
        mockMvc.perform(get("/api/editions/{id}/download", editionId)).andExpect(status().isOk());
        assertThat(fileRowExists(fileId)).isTrue();

        adminDelete(admin, "/api/admin/works/{id}", workId).andExpect(status().isNoContent());

        adminGet(admin, "/api/admin/works/{id}", workId).andExpect(status().isNotFound());
        adminGet(admin, "/api/admin/editions/{id}", editionId).andExpect(status().isNotFound());
        adminGet(admin, "/api/admin/editions/{id}", infoEditionId).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/editions/{id}/download", editionId)).andExpect(status().isNotFound());
        assertThat(fileRowExists(fileId)).isFalse();
        assertThat(fileRowExists(previewFileId)).isFalse();

        // 작곡가는 남고 곡 수만 준다
        adminGet(admin, "/api/admin/composers/{id}", composerId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.workCount").value(0));

        adminDelete(admin, "/api/admin/works/{id}", workId)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("NOT_FOUND"));
    }

    // ===== helpers =====

    private void assertListIds(Tokens admin, long composerId, String status, List<Long> expected) throws Exception {
        JsonNode page = data(adminQuery(admin, "/api/admin/works", "composerId", String.valueOf(composerId), "status", status)
                .andExpect(status().isOk()));
        assertThat(longs(page.path("works").path("content"), "id"))
                .as("status=" + status)
                .containsExactlyInAnyOrderElementsOf(expected);
    }
}
