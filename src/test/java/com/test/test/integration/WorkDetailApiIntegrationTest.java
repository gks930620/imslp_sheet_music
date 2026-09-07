package com.test.test.integration;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GET /api/works/{id} — docs/설계/02_API_명세서.md §3-3 (WorkDetailDTO, EditionDTO §2-3).
 */
class WorkDetailApiIntegrationTest extends SheetMusicFixtureSupport {

    private static final String MOONLIGHT = "Piano Sonata No.14, Op.27 No.2";

    @Test
    @DisplayName("시드 곡(월광) 상세: 기본 필드·별칭·IMSLP 링크·추천 없음(PREPARING)·같은 작곡가 곡 4개")
    void seedWork_detailFields() throws Exception {
        long id = findSeedWorkId("월광", MOONLIGHT);

        mockMvc.perform(get("/api/works/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(id))
                .andExpect(jsonPath("$.data.titleKo").value("월광 소나타"))
                .andExpect(jsonPath("$.data.titleOriginal").value(MOONLIGHT))
                .andExpect(jsonPath("$.data.composer.id").isNumber())
                .andExpect(jsonPath("$.data.composer.nameKo").value("베토벤"))
                .andExpect(jsonPath("$.data.composer.nameOriginal").value("Beethoven, Ludwig van"))
                .andExpect(jsonPath("$.data.catalogNumbers", hasSize(1)))
                .andExpect(jsonPath("$.data.catalogNumbers[0]").value("Op.27 No.2"))
                .andExpect(jsonPath("$.data.level").value("INTERMEDIATE"))
                .andExpect(jsonPath("$.data.status").value("PREPARING"))
                .andExpect(jsonPath("$.data.aliases").isArray())
                // 시드 값이 비어 있는 필드도 키는 있고 null (02 §0-4)
                .andExpect(jsonPath("$.data.compositionYear").value(nullValue()))
                .andExpect(jsonPath("$.data.musicalKey").value(nullValue()))
                .andExpect(jsonPath("$.data.movements").value(nullValue()))
                .andExpect(jsonPath("$.data.movementPageGuide").value(nullValue()))
                .andExpect(jsonPath("$.data.imslpUrl").value(
                        "https://imslp.org/wiki/Piano_Sonata_No.14,_Op.27_No.2_(Beethoven,_Ludwig_van)"))
                .andExpect(jsonPath("$.data.composerImslpUrl").value("https://imslp.org/wiki/Category:Beethoven,_Ludwig_van"))
                .andExpect(jsonPath("$.data.recommendedEdition").value(nullValue()))
                .andExpect(jsonPath("$.data.otherEditions", hasSize(0)))
                .andExpect(jsonPath("$.data.downloadableOtherCount").value(0))
                // 베토벤 시드 5곡 중 자기 제외 4곡 (최대 5)
                .andExpect(jsonPath("$.data.sameComposerWorks", hasSize(4)));

        JsonNode data = getJson("/api/works/{id}", id).path("data");
        List<String> aliases = new ArrayList<>();
        data.path("aliases").forEach(a -> aliases.add(a.asText()));
        assertThat(aliases).contains("월광", "월광 소나타", "Moonlight Sonata");
        for (JsonNode w : data.path("sameComposerWorks")) {
            assertThat(w.path("id").asLong()).isNotEqualTo(id);
            assertThat(w.path("composer").path("nameKo").asText()).isEqualTo("베토벤");
            assertThat(w.path("matchedAlias").isNull()).isTrue();
        }
    }

    @Test
    @DisplayName("추천 판본(FREE+파일) → status READY, EditionDTO 전 필드, downloadUrl 채워짐, 미리보기는 /uploads 로 서빙")
    void recommendedEdition_readyFields() throws Exception {
        Tokens admin = loginAdmin();
        long composerId = createComposer(admin, "상세테스트", "Detailtest, Zz");
        ReadyWork rw = createWorkWithRecommendedEdition(admin, composerId, "zzdetail 준비됨", "Zzdetail Ready",
                List.of("Op.1"), List.of("zz별칭"), "ELEMENTARY", 14, "FREE");
        long fileSize = samplePdfBytes().length;

        mockMvc.perform(get("/api/works/{id}", rw.workId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("READY"))
                .andExpect(jsonPath("$.data.aliases[0]").value("zz별칭"))
                .andExpect(jsonPath("$.data.catalogNumbers[0]").value("Op.1"))
                .andExpect(jsonPath("$.data.recommendedEdition.id").value(rw.editionId()))
                .andExpect(jsonPath("$.data.recommendedEdition.kind").value("COMPLETE_SCORE"))
                .andExpect(jsonPath("$.data.recommendedEdition.scope").value("COMPLETE"))
                .andExpect(jsonPath("$.data.recommendedEdition.movementNumber").value(nullValue()))
                .andExpect(jsonPath("$.data.recommendedEdition.sectionLabel").value(nullValue()))
                .andExpect(jsonPath("$.data.recommendedEdition.pageCount").value(14))
                .andExpect(jsonPath("$.data.recommendedEdition.fileSize").value(fileSize))
                .andExpect(jsonPath("$.data.recommendedEdition.hasFile").value(true))
                .andExpect(jsonPath("$.data.recommendedEdition.previewUrl", startsWith("/uploads/")))
                .andExpect(jsonPath("$.data.recommendedEdition.publisher").value("Test Verlag"))
                .andExpect(jsonPath("$.data.recommendedEdition.publishYear").value(1900))
                .andExpect(jsonPath("$.data.recommendedEdition.plateNumber").value("T.V. 1"))
                .andExpect(jsonPath("$.data.recommendedEdition.editor").value("Test Editor"))
                .andExpect(jsonPath("$.data.recommendedEdition.arranger").value(nullValue()))
                .andExpect(jsonPath("$.data.recommendedEdition.scanner").value(nullValue()))
                .andExpect(jsonPath("$.data.recommendedEdition.koreaCopyright").value("FREE"))
                .andExpect(jsonPath("$.data.recommendedEdition.imslpCopyrightText").value("Public Domain"))
                .andExpect(jsonPath("$.data.recommendedEdition.ccLicenseName").value(nullValue()))
                .andExpect(jsonPath("$.data.recommendedEdition.ccAttribution").value(nullValue()))
                .andExpect(jsonPath("$.data.recommendedEdition.imslpFileUrl")
                        .value("https://imslp.org/wiki/Special:ImagefromIndex/00001"))
                .andExpect(jsonPath("$.data.recommendedEdition.downloadable").value(true))
                .andExpect(jsonPath("$.data.recommendedEdition.largeFile").value(false))
                .andExpect(jsonPath("$.data.recommendedEdition.downloadUrl")
                        .value("/api/editions/" + rw.editionId() + "/download"))
                .andExpect(jsonPath("$.data.otherEditions", hasSize(0)))
                .andExpect(jsonPath("$.data.downloadableOtherCount").value(0));

        // 미리보기는 기존 /uploads 프록시로 실제 서빙된다 (02 §0-4)
        String previewUrl = getJson("/api/works/{id}", rw.workId())
                .path("data").path("recommendedEdition").path("previewUrl").asText();
        assertThat(previewUrl).endsWith(".png");
        mockMvc.perform(get(previewUrl)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("추천 판본이 RESTRICTED / UNKNOWN 이면 status 도 같고 downloadable=false, downloadUrl=null")
    void recommendedEdition_notDownloadable() throws Exception {
        Tokens admin = loginAdmin();
        long composerId = createComposer(admin, "제한테스트", "Restricted, Zz");
        ReadyWork restricted = createWorkWithRecommendedEdition(admin, composerId, "zz제한", "Zz Restricted",
                List.of(), List.of(), "INTERMEDIATE", 3, "RESTRICTED");
        ReadyWork unknown = createWorkWithRecommendedEdition(admin, composerId, "zz확인중", "Zz Unknown",
                List.of(), List.of(), "INTERMEDIATE", 3, "UNKNOWN");

        mockMvc.perform(get("/api/works/{id}", restricted.workId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RESTRICTED"))
                .andExpect(jsonPath("$.data.recommendedEdition.koreaCopyright").value("RESTRICTED"))
                .andExpect(jsonPath("$.data.recommendedEdition.hasFile").value(true))
                .andExpect(jsonPath("$.data.recommendedEdition.downloadable").value(false))
                .andExpect(jsonPath("$.data.recommendedEdition.downloadUrl").value(nullValue()));

        mockMvc.perform(get("/api/works/{id}", unknown.workId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("UNKNOWN"))
                .andExpect(jsonPath("$.data.recommendedEdition.downloadable").value(false))
                .andExpect(jsonPath("$.data.recommendedEdition.downloadUrl").value(nullValue()));
    }

    @Test
    @DisplayName("otherEditions: 추천 제외, id 오름차순, downloadableOtherCount 는 파일+FREE 인 것만")
    void otherEditions_orderAndCount() throws Exception {
        Tokens admin = loginAdmin();
        long composerId = createComposer(admin, "판본테스트", "Editions, Zz");
        ReadyWork rw = createReadyWork(admin, composerId, "zz판본들", "Zz Editions", 10);
        long e2 = createEdition(admin, rw.workId(), uploadSamplePdf(admin), 12, "FREE", null);      // 받기 가능
        long e3 = createEdition(admin, rw.workId(), uploadSamplePdf(admin), 9, "UNKNOWN", null);    // 파일 있으나 확인 중
        long e4 = createEdition(admin, rw.workId(), null, null, "FREE", null);                       // 파일 없음
        long e5 = createEdition(admin, rw.workId(), uploadSamplePdf(admin), 4, "FREE",
                Map.of("scope", "MOVEMENT", "movementNumber", 2));                                    // 2악장만, 받기 가능

        JsonNode data = getJson("/api/works/{id}", rw.workId()).path("data");
        assertThat(data.path("recommendedEdition").path("id").asLong()).isEqualTo(rw.editionId());

        List<Long> otherIds = new ArrayList<>();
        data.path("otherEditions").forEach(e -> otherIds.add(e.path("id").asLong()));
        // 관리자 직접 등록 판본은 imslp_download_count 가 전부 null → id ASC
        assertThat(otherIds).containsExactly(e2, e3, e4, e5);
        assertThat(data.path("downloadableOtherCount").asInt()).isEqualTo(2);

        for (JsonNode e : data.path("otherEditions")) {
            long id = e.path("id").asLong();
            if (id == e2) {
                assertThat(e.path("downloadable").asBoolean()).isTrue();
                assertThat(e.path("downloadUrl").asText()).isEqualTo("/api/editions/" + e2 + "/download");
                assertThat(e.path("pageCount").asInt()).isEqualTo(12);
            }
            if (id == e3) {
                assertThat(e.path("hasFile").asBoolean()).isTrue();
                assertThat(e.path("downloadable").asBoolean()).isFalse();
                assertThat(e.path("downloadUrl").isNull()).isTrue();
            }
            if (id == e4) {
                assertThat(e.path("hasFile").asBoolean()).isFalse();
                assertThat(e.path("fileSize").isNull()).isTrue();
                assertThat(e.path("previewUrl").isNull()).isTrue();
                assertThat(e.path("downloadable").asBoolean()).isFalse();
                assertThat(e.path("imslpFileUrl").asText()).isEqualTo("https://imslp.org/wiki/Special:ImagefromIndex/00001");
            }
            if (id == e5) {
                assertThat(e.path("scope").asText()).isEqualTo("MOVEMENT");
                assertThat(e.path("movementNumber").asInt()).isEqualTo(2);
                assertThat(e.path("downloadable").asBoolean()).isTrue();
            }
        }
    }

    @Test
    @DisplayName("sameComposerWorks: 같은 작곡가의 다른 공개 곡, 최대 5, 자기 자신·숨김 제외")
    void sameComposerWorks_maxFiveExcludingSelfAndHidden() throws Exception {
        Tokens admin = loginAdmin();
        long composerId = createComposer(admin, "다작테스트", "Prolific, Zz");
        long self = createWork(admin, composerId, "zz본곡", "Zz Self");
        List<Long> others = new ArrayList<>();
        for (int i = 1; i <= 6; i++) {
            others.add(createWork(admin, composerId, "zz다른곡 " + i, "Zz Other " + i));
        }
        long hidden = createWork(admin, composerId, "zz숨김곡", "Zz Hidden", List.of(), List.of(), null, true);

        JsonNode list = getJson("/api/works/{id}", self).path("data").path("sameComposerWorks");
        assertThat(list).hasSize(5);
        for (JsonNode w : list) {
            long id = w.path("id").asLong();
            assertThat(id).isNotEqualTo(self).isNotEqualTo(hidden);
            assertThat(others).contains(id);
            assertThat(w.path("composer").path("id").asLong()).isEqualTo(composerId);
        }
    }

    @Test
    @DisplayName("없는 id → 404 NOT_FOUND")
    void notFound_404() throws Exception {
        mockMvc.perform(get("/api/works/{id}", 999_999_999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("숨김 곡 → 404 NOT_FOUND (사용자에게는 없는 곡)")
    void hiddenWork_404() throws Exception {
        Tokens admin = loginAdmin();
        long composerId = createComposer(admin, "숨김상세", "Hiddendetail, Zz");
        long hidden = createWork(admin, composerId, "zz숨김", "Zz Hidden Detail", List.of(), List.of(), null, true);

        mockMvc.perform(get("/api/works/{id}", hidden))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("id 가 숫자가 아니면 400 TYPE_MISMATCH")
    void invalidId_400() throws Exception {
        mockMvc.perform(get("/api/works/{id}", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("TYPE_MISMATCH"));
    }
}
