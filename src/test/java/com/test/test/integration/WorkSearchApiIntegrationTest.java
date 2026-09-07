package com.test.test.integration;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GET /api/works/search — docs/설계/02_API_명세서.md §3-1.
 * 인수조건(기획 01 §6 "검색")은 실제 시드(작곡가 25·곡 50, 01_ERD §6)로 검증하고,
 * 판본이 필요한 필터·정렬은 관리자 API 로 만든 데이터로 검증한다.
 */
class WorkSearchApiIntegrationTest extends SheetMusicFixtureSupport {

    private static final String MOONLIGHT = "Piano Sonata No.14, Op.27 No.2";
    private static final String FUR_ELISE = "Für Elise, WoO 59";

    // ===== 응답 형태 =====

    @Test
    @DisplayName("응답 형태: q, composers[], composerMatchCount, unfilteredTotal, works(PageResponse<WorkSummaryDTO>)")
    void search_responseShape() throws Exception {
        mockMvc.perform(get("/api/works/search").param("q", "월광"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.q").value("월광"))
                .andExpect(jsonPath("$.data.composers").isArray())
                .andExpect(jsonPath("$.data.composerMatchCount").value(0))
                .andExpect(jsonPath("$.data.unfilteredTotal").value(1))
                .andExpect(jsonPath("$.data.works.content", hasSize(1)))
                .andExpect(jsonPath("$.data.works.page").value(0))
                .andExpect(jsonPath("$.data.works.size").value(20))
                .andExpect(jsonPath("$.data.works.totalElements").value(1))
                .andExpect(jsonPath("$.data.works.totalPages").value(1))
                .andExpect(jsonPath("$.data.works.first").value(true))
                .andExpect(jsonPath("$.data.works.last").value(true))
                // WorkSummaryDTO — 시드 곡은 판본 0개 → PREPARING, 판본 정보 null (키는 생략하지 않는다)
                .andExpect(jsonPath("$.data.works.content[0].titleKo").value("월광 소나타"))
                .andExpect(jsonPath("$.data.works.content[0].titleOriginal").value(MOONLIGHT))
                .andExpect(jsonPath("$.data.works.content[0].composer.id").isNumber())
                .andExpect(jsonPath("$.data.works.content[0].composer.nameKo").value("베토벤"))
                .andExpect(jsonPath("$.data.works.content[0].composer.nameOriginal").value("Beethoven, Ludwig van"))
                .andExpect(jsonPath("$.data.works.content[0].catalogNumbers[0]").value("Op.27 No.2"))
                .andExpect(jsonPath("$.data.works.content[0].level").value("INTERMEDIATE"))
                .andExpect(jsonPath("$.data.works.content[0].status").value("PREPARING"))
                .andExpect(jsonPath("$.data.works.content[0].pageCount").value(nullValue()))
                .andExpect(jsonPath("$.data.works.content[0].fileSize").value(nullValue()))
                .andExpect(jsonPath("$.data.works.content[0].previewUrl").value(nullValue()))
                .andExpect(jsonPath("$.data.works.content[0].matchedAlias").value("월광"));
    }

    // ===== 인수조건 (시드) =====

    @Nested
    @DisplayName("기획 01 §6 검색 인수조건 — 시드로 검증")
    class AcceptanceCriteria {

        @Test
        @DisplayName("\"월광\" → 베토벤 피아노 소나타 14번 (별칭 일치, matchedAlias=\"월광\")")
        void moonlight_byAlias() throws Exception {
            JsonNode content = searchWorks("월광");
            assertThat(content).hasSize(1);
            assertThat(content.get(0).path("titleOriginal").asText()).isEqualTo(MOONLIGHT);
            assertThat(content.get(0).path("matchedAlias").asText()).isEqualTo("월광");
        }

        @ParameterizedTest(name = "\"{0}\" → Für Elise")
        @ValueSource(strings = {"엘리제를 위하여", "엘리제", "Für Elise", "fur elise", "FUR ELISE", "Fuer Elise"})
        void furElise_allVariantsHitSameWork(String q) throws Exception {
            long expected = findSeedWorkId("엘리제를 위하여", FUR_ELISE);
            assertThat(searchWorkIds(q)).as("q=%s", q).contains(expected);
        }

        @Test
        @DisplayName("\"쇼팽 녹턴\" → 쇼팽의 녹턴만 (AND 매칭), 다른 작곡가의 녹턴은 제외, matchedAlias 는 null")
        void chopinNocturne_andMatch() throws Exception {
            JsonNode content = searchWorks("쇼팽 녹턴");
            assertThat(content.size()).isGreaterThanOrEqualTo(2); // Nocturnes Op.9, Nocturne B.49
            List<String> titles = new ArrayList<>();
            for (JsonNode item : content) {
                assertThat(item.path("composer").path("nameKo").asText()).isEqualTo("쇼팽");
                // "녹턴" 은 title_ko 로 설명되므로 별칭 표시 없음 (02 §3-1 6번 예시)
                assertThat(item.path("matchedAlias").isNull())
                        .as("matchedAlias of %s", item.path("titleOriginal")).isTrue();
                titles.add(item.path("titleOriginal").asText());
            }
            assertThat(titles).contains("Nocturnes, Op.9", "Nocturne in C-sharp minor, B.49");
            // 리스트 "사랑의 꿈"(별칭 "녹턴 3번 리스트")은 "쇼팽" 에 안 걸려 제외
            assertThat(titles).doesNotContain("Liebesträume, S.541");
        }

        @Test
        @DisplayName("\"Chopin\" 과 \"쇼팽\" 은 같은 곡들을 낸다 (작곡가 별칭·원어 표기)")
        void chopin_koreanAndOriginal_sameWorks() throws Exception {
            List<Long> byOriginal = searchWorkIds("Chopin");
            List<Long> byKorean = searchWorkIds("쇼팽");
            assertThat(byOriginal).isNotEmpty();
            assertThat(byOriginal).containsExactlyInAnyOrderElementsOf(byKorean);
        }

        @ParameterizedTest(name = "\"{0}\" → 월광 소나타 (작품번호 표기 변형)")
        @ValueSource(strings = {"Op.27 No.2", "op 27 no 2", "op27no2", "Op. 27, No. 2"})
        void catalogNumber_variants(String q) throws Exception {
            long expected = findSeedWorkId("월광", MOONLIGHT);
            assertThat(searchWorkIds(q)).as("q=%s", q).contains(expected);
        }

        @Test
        @DisplayName("\"BWV 846\" = \"BWV846\", \"K.545\" = \"K545\"")
        void catalogNumber_spaceAndDotInsensitive() throws Exception {
            assertThat(searchWorkIds("BWV 846")).isNotEmpty().isEqualTo(searchWorkIds("BWV846"));
            assertThat(searchWorks("BWV846").get(0).path("titleOriginal").asText())
                    .isEqualTo("Prelude and Fugue in C major, BWV 846");

            assertThat(searchWorkIds("K.545")).isNotEmpty().isEqualTo(searchWorkIds("K545"));
            assertThat(searchWorks("K545").get(0).path("titleOriginal").asText())
                    .isEqualTo("Piano Sonata No.16 in C major, K.545");
        }

        @Test
        @DisplayName("대소문자 무시: 결과 목록(순서 포함)이 같다")
        void caseInsensitive() throws Exception {
            assertThat(searchWorkIds("MOONLIGHT SONATA")).isNotEmpty().isEqualTo(searchWorkIds("moonlight sonata"));
            assertThat(searchWorkIds("CHOPIN")).isNotEmpty().isEqualTo(searchWorkIds("chopin"));
        }

        @Test
        @DisplayName("부분 일치: \"녹\" 만 쳐도 녹턴 곡들이 나온다")
        void partialMatch() throws Exception {
            List<String> titles = new ArrayList<>();
            for (JsonNode item : searchWorks("녹")) {
                titles.add(item.path("titleOriginal").asText());
            }
            assertThat(titles).contains("Nocturnes, Op.9", "Nocturne in C-sharp minor, B.49");
        }

        @Test
        @DisplayName("작곡가 카드: \"베토벤\" → composers[0]=베토벤(workCount 5), composerMatchCount 1, 곡 5")
        void composerCard_beethoven() throws Exception {
            mockMvc.perform(get("/api/works/search").param("q", "베토벤"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.composers", hasSize(1)))
                    .andExpect(jsonPath("$.data.composers[0].id").isNumber())
                    .andExpect(jsonPath("$.data.composers[0].nameKo").value("베토벤"))
                    .andExpect(jsonPath("$.data.composers[0].nameOriginal").value("Beethoven, Ludwig van"))
                    .andExpect(jsonPath("$.data.composers[0].workCount").value(5))
                    .andExpect(jsonPath("$.data.composerMatchCount").value(1))
                    .andExpect(jsonPath("$.data.works.totalElements").value(5))
                    // 작곡가 이름으로 설명되는 검색어 → matchedAlias 전부 null
                    .andExpect(jsonPath("$.data.works.content[*].matchedAlias").value(everyItem(nullValue())));
        }

        @Test
        @DisplayName("작곡가 카드는 곡 수 많은 순 최대 3명, composerMatchCount 는 전체 수")
        void composerCard_limitThree() throws Exception {
            // "ch": Chopin(10)·Bach(3)·Schumann(2)·Schubert·Tchaikovsky·Rachmaninoff·Hanon(Charles)·Kuhlau/Burgmüller(Friedrich) …
            MvcResult result = mockMvc.perform(get("/api/works/search").param("q", "ch"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.composers", hasSize(3)))
                    .andExpect(jsonPath("$.data.composers[0].nameKo").value("쇼팽"))
                    .andReturn();
            JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
            assertThat(data.path("composerMatchCount").asInt()).isGreaterThan(3);
            JsonNode composers = data.path("composers");
            assertThat(composers.get(0).path("workCount").asInt())
                    .isGreaterThanOrEqualTo(composers.get(1).path("workCount").asInt());
            assertThat(composers.get(1).path("workCount").asInt())
                    .isGreaterThanOrEqualTo(composers.get(2).path("workCount").asInt());
        }

        @Test
        @DisplayName("작곡가 카드는 필터와 무관: 필터로 곡이 0건이어도 카드는 남는다")
        void composerCard_independentOfFilters() throws Exception {
            mockMvc.perform(get("/api/works/search").param("q", "베토벤").param("downloadable", "true"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.composers", hasSize(1)))
                    .andExpect(jsonPath("$.data.works.totalElements").value(0))
                    .andExpect(jsonPath("$.data.unfilteredTotal").value(5));
        }

        @Test
        @DisplayName("matchedAlias: 작품번호로 설명되는 단어는 null (\"BWV 846\")")
        void matchedAlias_nullWhenExplainedByCatalog() throws Exception {
            JsonNode content = searchWorks("BWV 846");
            assertThat(content.get(0).path("matchedAlias").isNull()).isTrue();
        }

        @Test
        @DisplayName("matchedAlias: 별칭으로만 설명되는 단어는 그 별칭 원문 (\"터키행진곡\" → 소나타 11번)")
        void matchedAlias_aliasOriginalText() throws Exception {
            JsonNode content = searchWorks("터키행진곡");
            assertThat(content).hasSize(1);
            assertThat(content.get(0).path("titleOriginal").asText())
                    .isEqualTo("Piano Sonata No.11 in A major, K.331/300i");
            assertThat(content.get(0).path("matchedAlias").asText()).isEqualTo("터키행진곡");
        }

        @Test
        @DisplayName("난이도 필터(시드): \"체르니\" level=ELEMENTARY → 초급 2곡, unfilteredTotal 3")
        void levelFilter_seed() throws Exception {
            mockMvc.perform(get("/api/works/search").param("q", "체르니").param("level", "ELEMENTARY"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.works.totalElements").value(2))
                    .andExpect(jsonPath("$.data.unfilteredTotal").value(3))
                    .andExpect(jsonPath("$.data.works.content[*].level").value(everyItem(is("ELEMENTARY"))));

            mockMvc.perform(get("/api/works/search").param("q", "체르니").param("level", "ELEMENTARY,INTERMEDIATE"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.works.totalElements").value(3));
        }
    }

    // ===== 필터 (판본 필요 → 관리자 API 로 생성) =====

    @Nested
    @DisplayName("필터 3종 — pages / downloadable / level(미정 제외)")
    class Filters {

        @Test
        @DisplayName("pages=LE10 / 11_20 / GE21 은 추천 판본 쪽수 구간, 추천 판본 없는 곡 제외, unfilteredTotal 은 필터 전 수")
        void pagesFilter() throws Exception {
            Tokens admin = loginAdmin();
            long composerId = createComposer(admin, "쪽수테스트", "Pagesfilter, Zz");
            long le10 = createReadyWork(admin, composerId, "zzpages 여덟쪽", "Zzpages Eight", 8).workId();
            long mid = createReadyWork(admin, composerId, "zzpages 열넷쪽", "Zzpages Fourteen", 14).workId();
            long ge21 = createWorkWithRecommendedEdition(admin, composerId, "zzpages 스물다섯쪽", "Zzpages TwentyFive",
                    List.of(), List.of(), "ADVANCED", 25, "UNKNOWN").workId();
            long noEdition = createWork(admin, composerId, "zzpages 판본없음", "Zzpages None");

            assertThat(searchWorkIds("zzpages")).containsExactlyInAnyOrder(le10, mid, ge21, noEdition);

            mockMvc.perform(get("/api/works/search").param("q", "zzpages").param("pages", "LE10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.works.totalElements").value(1))
                    .andExpect(jsonPath("$.data.works.content[0].id").value(le10))
                    .andExpect(jsonPath("$.data.works.content[0].pageCount").value(8))
                    .andExpect(jsonPath("$.data.unfilteredTotal").value(4));

            mockMvc.perform(get("/api/works/search").param("q", "zzpages").param("pages", "11_20"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.works.totalElements").value(1))
                    .andExpect(jsonPath("$.data.works.content[0].id").value(mid));

            mockMvc.perform(get("/api/works/search").param("q", "zzpages").param("pages", "GE21"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.works.totalElements").value(1))
                    .andExpect(jsonPath("$.data.works.content[0].id").value(ge21));
        }

        @Test
        @DisplayName("pages 에 정의되지 않은 값 → 400")
        void pagesFilter_invalidValue() throws Exception {
            mockMvc.perform(get("/api/works/search").param("q", "월광").param("pages", "LE5"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false));
        }

        @Test
        @DisplayName("downloadable=true → status=READY 만 (준비 중·이용 제한·확인 중 제외)")
        void downloadableFilter() throws Exception {
            Tokens admin = loginAdmin();
            long composerId = createComposer(admin, "받기테스트", "Downloadable, Zz");
            long ready = createReadyWork(admin, composerId, "zzdl 준비됨", "Zzdl Ready", 5).workId();
            long restricted = createWorkWithRecommendedEdition(admin, composerId, "zzdl 제한", "Zzdl Restricted",
                    List.of(), List.of(), "INTERMEDIATE", 5, "RESTRICTED").workId();
            long unknown = createWorkWithRecommendedEdition(admin, composerId, "zzdl 확인중", "Zzdl Unknown",
                    List.of(), List.of(), "INTERMEDIATE", 5, "UNKNOWN").workId();
            long preparing = createWork(admin, composerId, "zzdl 준비중", "Zzdl Preparing");

            assertThat(searchWorkIds("zzdl")).containsExactlyInAnyOrder(ready, restricted, unknown, preparing);

            mockMvc.perform(get("/api/works/search").param("q", "zzdl").param("downloadable", "true"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.works.totalElements").value(1))
                    .andExpect(jsonPath("$.data.works.content[0].id").value(ready))
                    .andExpect(jsonPath("$.data.works.content[0].status").value("READY"))
                    .andExpect(jsonPath("$.data.unfilteredTotal").value(4));

            // 필터 없이 보면 상태가 4종 모두 계산돼 있다 (01_ERD §4 규칙)
            for (JsonNode item : searchWorks("zzdl")) {
                long id = item.path("id").asLong();
                String status = item.path("status").asText();
                if (id == ready) assertThat(status).isEqualTo("READY");
                if (id == restricted) assertThat(status).isEqualTo("RESTRICTED");
                if (id == unknown) assertThat(status).isEqualTo("UNKNOWN");
                if (id == preparing) assertThat(status).isEqualTo("PREPARING");
            }
        }

        @Test
        @DisplayName("level 지정 시 난이도 미정(null) 곡은 제외된다")
        void levelFilter_excludesUnsetLevel() throws Exception {
            Tokens admin = loginAdmin();
            long composerId = createComposer(admin, "난이도테스트", "Levelfilter, Zz");
            long elementary = createWork(admin, composerId, "zzlevel 초급", "Zzlevel Elementary",
                    List.of(), List.of(), "ELEMENTARY", false);
            long unset = createWork(admin, composerId, "zzlevel 미정", "Zzlevel Unset",
                    List.of(), List.of(), null, false);

            assertThat(searchWorkIds("zzlevel")).containsExactlyInAnyOrder(elementary, unset);

            mockMvc.perform(get("/api/works/search").param("q", "zzlevel").param("level", "ELEMENTARY"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.works.totalElements").value(1))
                    .andExpect(jsonPath("$.data.works.content[0].id").value(elementary))
                    .andExpect(jsonPath("$.data.unfilteredTotal").value(2));

            // 미정 곡은 level=null 로 내려온다 (키 생략 없음)
            for (JsonNode item : searchWorks("zzlevel")) {
                if (item.path("id").asLong() == unset) {
                    assertThat(item.has("level")).isTrue();
                    assertThat(item.path("level").isNull()).isTrue();
                }
            }
        }

        @Test
        @DisplayName("level 에 정의되지 않은 값 → 400")
        void levelFilter_invalidValue() throws Exception {
            mockMvc.perform(get("/api/works/search").param("q", "월광").param("level", "HARD"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false));
        }
    }

    // ===== 정렬 · 페이지 · 숨김 =====

    @Test
    @DisplayName("정렬: 일치도(제목·별칭 정확 3 > 전방 2 > 그 외 1) 내림차순 → id 오름차순")
    void ordering_byRelevanceThenId() throws Exception {
        Tokens admin = loginAdmin();
        long composerId = createComposer(admin, "정렬테스트", "Ranking, Zz");
        // 만드는 순서를 일부러 거꾸로 해 id 순이 아니라 일치도 순임을 드러낸다
        long other = createWork(admin, composerId, "다른 곡 zzrank", "Other Zzrank Piece",
                List.of(), List.of(), "INTERMEDIATE", false);
        long prefix = createWork(admin, composerId, "zzrank 앞부분 일치", "Prefix Piece",
                List.of(), List.of(), "INTERMEDIATE", false);
        long exactAlias = createWork(admin, composerId, "별칭 정확 일치 곡", "Exact Alias Piece",
                List.of(), List.of("zzrank"), "INTERMEDIATE", false);
        long exactTitle = createWork(admin, composerId, "zzrank", "Exact Title Piece",
                List.of(), List.of(), "INTERMEDIATE", false);

        List<Long> ids = searchWorkIds("zzrank");
        assertThat(ids).hasSize(4);
        // 일치도 3: exactAlias(id 작음) → exactTitle, 일치도 2: prefix, 일치도 1: other
        assertThat(ids).containsExactly(exactAlias, exactTitle, prefix, other);
    }

    @Test
    @DisplayName("페이지: 21곡이면 page=0 은 20개(last=false), page=1 은 1개(last=true), 범위 밖은 200 + 빈 content")
    void paging_21Results() throws Exception {
        Tokens admin = loginAdmin();
        long composerId = createComposer(admin, "페이지테스트", "Paging, Zz");
        for (int i = 1; i <= 21; i++) {
            createWork(admin, composerId, "zzpaging 곡 " + i, "Zzpaging Piece " + i);
        }

        mockMvc.perform(get("/api/works/search").param("q", "zzpaging"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.works.content", hasSize(20)))
                .andExpect(jsonPath("$.data.works.page").value(0))
                .andExpect(jsonPath("$.data.works.size").value(20))
                .andExpect(jsonPath("$.data.works.totalElements").value(21))
                .andExpect(jsonPath("$.data.works.totalPages").value(2))
                .andExpect(jsonPath("$.data.works.first").value(true))
                .andExpect(jsonPath("$.data.works.last").value(false))
                .andExpect(jsonPath("$.data.unfilteredTotal").value(21));

        mockMvc.perform(get("/api/works/search").param("q", "zzpaging").param("page", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.works.content", hasSize(1)))
                .andExpect(jsonPath("$.data.works.page").value(1))
                .andExpect(jsonPath("$.data.works.first").value(false))
                .andExpect(jsonPath("$.data.works.last").value(true));

        mockMvc.perform(get("/api/works/search").param("q", "zzpaging").param("page", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.works.content", hasSize(0)))
                .andExpect(jsonPath("$.data.works.totalElements").value(21));
    }

    @Test
    @DisplayName("size 상한 100: size=500 을 보내도 100 으로 잘린다")
    void paging_sizeCappedAt100() throws Exception {
        mockMvc.perform(get("/api/works/search").param("q", "op").param("size", "500"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.works.size").value(100));
    }

    @Test
    @DisplayName("숨김 곡은 검색에 나오지 않는다 (제목으로도, 별칭으로도)")
    void hiddenWork_excluded() throws Exception {
        Tokens admin = loginAdmin();
        long composerId = createComposer(admin, "숨김테스트", "Hiddenwork, Zz");
        long visible = createWork(admin, composerId, "zzhidden 보임", "Zzhidden Visible",
                List.of(), List.of(), "INTERMEDIATE", false);
        createWork(admin, composerId, "zzhidden 숨김", "Zzhidden Hidden",
                List.of(), List.of("zzhiddenalias"), "INTERMEDIATE", true);

        mockMvc.perform(get("/api/works/search").param("q", "zzhidden"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.works.totalElements").value(1))
                .andExpect(jsonPath("$.data.unfilteredTotal").value(1))
                .andExpect(jsonPath("$.data.works.content[0].id").value(visible));

        mockMvc.perform(get("/api/works/search").param("q", "zzhiddenalias"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.works.totalElements").value(0));
    }

    @Test
    @DisplayName("검색 0건: 200 + 빈 content, composers 빈 배열")
    void noResults() throws Exception {
        mockMvc.perform(get("/api/works/search").param("q", "zzq없는검색어zz"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.works.content", hasSize(0)))
                .andExpect(jsonPath("$.data.works.totalElements").value(0))
                .andExpect(jsonPath("$.data.composers", hasSize(0)))
                .andExpect(jsonPath("$.data.composerMatchCount").value(0))
                .andExpect(jsonPath("$.data.unfilteredTotal").value(0));
    }

    // ===== 400 =====

    @Test
    @DisplayName("q 가 공백만 → 400 VALIDATION_ERROR, errors[].field=q")
    void blankQuery_400() throws Exception {
        mockMvc.perform(get("/api/works/search").param("q", "   "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[*].field", hasItem("q")));
    }

    @Test
    @DisplayName("q 가 기호만이라 정규화 후 단어 0개 → 400 VALIDATION_ERROR")
    void symbolsOnlyQuery_400() throws Exception {
        mockMvc.perform(get("/api/works/search").param("q", "... - / ()"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[*].field", hasItem("q")));
    }

    @Test
    @DisplayName("q 100자 초과 → 400 VALIDATION_ERROR, 100자는 허용")
    void tooLongQuery_400() throws Exception {
        mockMvc.perform(get("/api/works/search").param("q", "가".repeat(101)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
        mockMvc.perform(get("/api/works/search").param("q", "가".repeat(100)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("q 누락 → 400")
    void missingQuery_400() throws Exception {
        mockMvc.perform(get("/api/works/search"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }
}
