package com.test.test.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 시드 적재 — docs/설계/01_ERD.md §6, 03_기술결정.md §8.
 * {@code src/main/resources/seed/composers.csv, works.csv} 를 {@code SeedLoader}(ApplicationRunner, 빈 이름 seedLoader)
 * 가 기동 시 적재한다. 기획 03 표(작곡가 25·곡 50)와 1:1 이어야 하며, 멱등(재실행해도 행이 늘지 않음)이어야 한다.
 */
class SeedLoaderIntegrationTest extends SheetMusicFixtureSupport {

    private static final String SEED_LOADER_BEAN = "seedLoader";
    private static final String MOONLIGHT = "Piano Sonata No.14, Op.27 No.2";

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    @DisplayName("기동 후 작곡가 25명(무소르그스키 제외), 곡 50")
    void seed_counts() throws Exception {
        JsonNode composers = getJson("/api/composers").path("data");
        assertThat(composers.path("total").asInt()).isEqualTo(25);
        int workSum = 0;
        for (JsonNode c : composers.path("composers")) {
            workSum += c.path("workCount").asInt();
            assertThat(c.path("nameOriginal").asText()).isNotEqualTo("Mussorgsky, Modest");
        }
        assertThat(workSum).isEqualTo(50);

        Tokens admin = loginAdmin();
        assertThat(getJsonAsAdmin(admin, "/api/admin/works").path("data").path("unfilteredTotal").asInt())
                .isEqualTo(50);
    }

    @Test
    @DisplayName("\"월광\" → 소나타 14번: 별칭·작품번호·난이도·IMSLP 주소(정규 형태)·판본 0개(PREPARING)")
    void seed_moonlight() throws Exception {
        long id = findSeedWorkId("월광", MOONLIGHT);
        mockMvc.perform(get("/api/works/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.titleKo").value("월광 소나타"))
                .andExpect(jsonPath("$.data.composer.nameOriginal").value("Beethoven, Ludwig van"))
                .andExpect(jsonPath("$.data.catalogNumbers[0]").value("Op.27 No.2"))
                .andExpect(jsonPath("$.data.level").value("INTERMEDIATE"))
                .andExpect(jsonPath("$.data.status").value("PREPARING"))
                .andExpect(jsonPath("$.data.imslpUrl").value(
                        "https://imslp.org/wiki/Piano_Sonata_No.14,_Op.27_No.2_(Beethoven,_Ludwig_van)"))
                .andExpect(jsonPath("$.data.recommendedEdition").isEmpty());
    }

    @Test
    @DisplayName("난이도 매핑: 입문 BEGINNER(바이엘) / 초급 ELEMENTARY(엘리제) / 중급 INTERMEDIATE(월광) / 고급 ADVANCED(비창)")
    void seed_levels() throws Exception {
        assertThat(levelOf("바이엘 피아노 교본", "Vorschule im Klavierspiel, Op.101")).isEqualTo("BEGINNER");
        assertThat(levelOf("엘리제를 위하여", "Für Elise, WoO 59")).isEqualTo("ELEMENTARY");
        assertThat(levelOf("월광", MOONLIGHT)).isEqualTo("INTERMEDIATE");
        assertThat(levelOf("비창", "Piano Sonata No.8, Op.13")).isEqualTo("ADVANCED");
    }

    @Test
    @DisplayName("작품번호: 괄호 병기 \"D.899 (Op.90)\" 는 두 개로, \"(없음)\" 은 빈 목록")
    void seed_catalogNumbers() throws Exception {
        long schubert = findSeedWorkId("슈베르트 즉흥곡", "4 Impromptus, D.899");
        List<String> catalogs = new ArrayList<>();
        getJson("/api/works/{id}", schubert).path("data").path("catalogNumbers").forEach(c -> catalogs.add(c.asText()));
        assertThat(catalogs).containsExactly("D.899", "Op.90");

        long satie = findSeedWorkId("짐노페디", "3 Gymnopédies");
        assertThat(getJson("/api/works/{id}", satie).path("data").path("catalogNumbers")).isEmpty();
    }

    @Test
    @DisplayName("별칭: 띄어쓰기만 다른 변형은 정규화 중복으로 하나만 저장 (체르니 100번: 6개 중 5개)")
    void seed_aliasDedup() throws Exception {
        long czerny100 = findSeedWorkId("체르니 100번", "100 Übungsstücke, Op.139");
        List<String> aliases = new ArrayList<>();
        getJson("/api/works/{id}", czerny100).path("data").path("aliases").forEach(a -> aliases.add(a.asText()));
        assertThat(aliases).hasSize(5);
        assertThat(aliases).contains("체르니 100", "체르니 100번", "체르니 백번", "Czerny 100", "100 Progressive Studies");
        assertThat(aliases).doesNotContain("체르니100");
    }

    @Test
    @DisplayName("작곡가 별칭과 정보: 차이콥스키 — 생몰 1840–1893, 러시아, 별칭 6개, 작곡가 별칭+곡 별칭 조합 검색")
    void seed_composerAliases() throws Exception {
        long id = findSeedComposerId("Tchaikovsky, Pyotr");
        JsonNode data = getJson("/api/composers/{id}", id).path("data");
        assertThat(data.path("nameKo").asText()).isEqualTo("차이콥스키");
        assertThat(data.path("birthYear").asInt()).isEqualTo(1840);
        assertThat(data.path("deathYear").asInt()).isEqualTo(1893);
        assertThat(data.path("nationality").asText()).isEqualTo("러시아");
        List<String> aliases = new ArrayList<>();
        data.path("aliases").forEach(a -> aliases.add(a.asText()));
        assertThat(aliases).containsExactlyInAnyOrder(
                "차이코프스키", "챠이코프스키", "표트르 차이콥스키", "Tchaikovsky", "Tchaikovski", "Chaikovsky");
        // 03 §5 7번: "차이코프스키 사계" = 작곡가 별칭 + 곡 별칭
        assertThat(searchWorks("차이코프스키 사계").get(0).path("titleOriginal").asText())
                .isEqualTo("The Seasons, Op.37a");
    }

    @Test
    @DisplayName("비ASCII 주소도 정규 형태 그대로 저장: Für Elise")
    void seed_nonAsciiImslpUrl() throws Exception {
        long id = findSeedWorkId("엘리제를 위하여", "Für Elise, WoO 59");
        mockMvc.perform(get("/api/works/{id}", id))
                .andExpect(jsonPath("$.data.imslpUrl")
                        .value("https://imslp.org/wiki/Für_Elise,_WoO_59_(Beethoven,_Ludwig_van)"));
    }

    @Test
    @DisplayName("멱등: 로더를 한 번 더 실행해도 작곡가·곡·별칭 수가 그대로다 (재기동 = 재실행)")
    void seed_idempotentOnRerun() throws Exception {
        ApplicationRunner loader = applicationContext.getBean(SEED_LOADER_BEAN, ApplicationRunner.class);

        long moonlight = findSeedWorkId("월광", MOONLIGHT);
        int aliasesBefore = getJson("/api/works/{id}", moonlight).path("data").path("aliases").size();
        int totalBefore = getJson("/api/composers").path("data").path("total").asInt();

        loader.run(new DefaultApplicationArguments());

        JsonNode composers = getJson("/api/composers").path("data");
        assertThat(composers.path("total").asInt()).isEqualTo(totalBefore).isEqualTo(25);
        int workSum = 0;
        for (JsonNode c : composers.path("composers")) {
            workSum += c.path("workCount").asInt();
        }
        assertThat(workSum).isEqualTo(50);
        assertThat(getJson("/api/works/{id}", moonlight).path("data").path("aliases").size()).isEqualTo(aliasesBefore);
        assertThat(searchWorks("월광")).hasSize(1);
    }

    @Test
    @DisplayName("멱등·삽입 전용: 관리자가 고친 값은 로더 재실행이 덮어쓰지 않는다")
    void seed_rerunDoesNotOverwriteAdminEdits() throws Exception {
        Tokens admin = loginAdmin();
        long moonlight = findSeedWorkId("월광", MOONLIGHT);
        JsonNode current = getJsonAsAdmin(admin, "/api/admin/works/{id}", moonlight).path("data");

        // 난이도만 ADVANCED 로 바꾼다 (PUT 은 전체 교체이므로 기존 값을 그대로 실어 보낸다)
        ObjectNode body = objectMapper.createObjectNode();
        body.put("composerId", current.path("composer").path("id").asLong());
        body.put("titleKo", current.path("titleKo").asText());
        body.put("titleOriginal", current.path("titleOriginal").asText());
        body.set("catalogNumbers", current.path("catalogNumbers"));
        body.set("aliases", current.path("aliases"));
        body.put("level", "ADVANCED");
        body.set("compositionYear", current.path("compositionYear"));
        body.set("musicalKey", current.path("musicalKey"));
        body.set("movements", current.path("movements"));
        body.set("movementPageGuide", current.path("movementPageGuide"));
        body.put("imslpUrl", current.path("imslpUrl").asText());
        body.put("hidden", false);
        mockMvc.perform(put("/api/admin/works/{id}", moonlight)
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());

        applicationContext.getBean(SEED_LOADER_BEAN, ApplicationRunner.class).run(new DefaultApplicationArguments());

        mockMvc.perform(get("/api/works/{id}", moonlight))
                .andExpect(jsonPath("$.data.level").value("ADVANCED"));
    }

    private String levelOf(String q, String titleOriginal) throws Exception {
        for (JsonNode item : searchWorks(q)) {
            if (titleOriginal.equals(item.path("titleOriginal").asText())) {
                return item.path("level").asText();
            }
        }
        throw new AssertionError("not found: " + titleOriginal);
    }
}
