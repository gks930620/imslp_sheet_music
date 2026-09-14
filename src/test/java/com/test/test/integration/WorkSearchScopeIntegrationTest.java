package com.test.test.integration;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 검색 기준 {@code in} — docs/설계/02_API_명세서.md §3-1 "검색 기준 in", 기획 04 §4·§8-D·§8-E·§8-F.
 *
 * <p>세 가지를 본다.
 * <ol>
 *   <li><b>회귀</b> — {@code in=ALL} 이 기준이 없던 시절과 <b>한 글자도 다르지 않다</b>(기획 04 §7 충돌 4).
 *       이번 변경에서 가장 큰 위험이라 맨 앞에 둔다.</li>
 *   <li>TITLE / COMPOSER 가 찾는 칸의 배분(§3-1 표) — 작품번호는 TITLE 이다.</li>
 *   <li>알 수 없는 값의 <b>관용 처리</b>(오류 화면 금지)와 0건 출구 재료 {@code totalInAll}.</li>
 * </ol>
 *
 * <p>인수 조건은 실제 시드(작곡가 25·곡 50, 01_ERD §6)로 검증한다.
 */
class WorkSearchScopeIntegrationTest extends SheetMusicFixtureSupport {

    private static final String MOONLIGHT = "Piano Sonata No.14, Op.27 No.2";
    private static final String FUR_ELISE = "Für Elise, WoO 59";

    // ===== 응답 형태 · 관용 처리 =====

    @Test
    @DisplayName("in 을 생략하면 data.in 은 \"ALL\", totalInAll 은 null (질문이 성립하지 않는다)")
    void in_omitted_isAll_andTotalInAllIsNull() throws Exception {
        mockMvc.perform(get("/api/works/search").param("q", "월광"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.in").value("ALL"))
                .andExpect(jsonPath("$.data.totalInAll").doesNotExist());
    }

    @ParameterizedTest(name = "in={0} → \"{1}\" (200, 오류 화면 없음)")
    @DisplayName("in 은 대소문자를 무시하고, 알 수 없는 값은 오류가 아니라 ALL 이다 (기획 04 §4-5, 인수 조건 8-F 4)")
    @CsvSource({
            "ALL,ALL",
            "all,ALL",
            "TITLE,TITLE",
            "title,TITLE",
            "COMPOSER,COMPOSER",
            "composer,COMPOSER",
            "xyz,ALL",
            "TITLE_KO,ALL",
            "0,ALL"
    })
    void in_isLenient(String raw, String expected) throws Exception {
        mockMvc.perform(get("/api/works/search").param("q", "월광").param("in", raw))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.in").value(expected));
    }

    @Test
    @DisplayName("in= (빈 값) 도 ALL 이다 — 주소가 조금 상해도 결과를 통째로 뺏지 않는다")
    void in_blank_isAll() throws Exception {
        mockMvc.perform(get("/api/works/search").param("q", "월광").param("in", ""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.in").value("ALL"));
    }

    // ===== 1. 회귀 — in=ALL 은 지금 검색 그대로 =====

    @Nested
    @DisplayName("회귀: 기존 검색 인수 조건(01 §6)이 \"전체\" 기준에서 그대로 통과한다")
    class AllScopeRegression {

        @ParameterizedTest(name = "q=\"{0}\": in 생략 == in=ALL (결과 id 목록이 순서까지 같다)")
        @ValueSource(strings = {
                "월광", "엘리제를 위하여", "엘리제", "Für Elise", "fur elise",
                "쇼팽 녹턴", "Chopin", "쇼팽",
                "Op.27 No.2", "op 27 no 2", "op27no2", "BWV 846", "BWV846", "K.545", "K545",
                "MOONLIGHT SONATA", "녹"
        })
        void omittedIn_equalsExplicitAll(String q) throws Exception {
            assertThat(ids(search(q, "ALL"))).as("q=%s", q).isEqualTo(searchWorkIds(q));
        }

        @Test
        @DisplayName("in=ALL 은 작곡가 카드·unfilteredTotal 도 그대로 (\"베토벤\" → 카드 1명)")
        void allScope_keepsComposerCardAndTotals() throws Exception {
            JsonNode data = search("베토벤", "ALL");
            assertThat(data.path("composerMatchCount").asInt()).isEqualTo(1);
            assertThat(data.path("composers").get(0).path("nameKo").asText()).isEqualTo("베토벤");
            assertThat(data.path("unfilteredTotal").asLong())
                    .isEqualTo(data.path("works").path("totalElements").asLong());
        }
    }

    // ===== 2. TITLE — 곡 제목·별칭·원어 제목·작품번호 =====

    @Nested
    @DisplayName("in=TITLE — 한국어 제목·별칭·원어 제목·작품번호에서만 찾는다")
    class TitleScope {

        @ParameterizedTest(name = "in=TITLE, q=\"{0}\" → 월광 소나타")
        @DisplayName("한국어 제목·별칭·원어 제목이 모두 곡명에 포함된다 (인수 조건 8-D 3)")
        @ValueSource(strings = {"월광", "월광 소나타", "Moonlight Sonata", "Piano Sonata No.14"})
        void title_koAliasOriginal(String q) throws Exception {
            assertThat(ids(search(q, "TITLE"))).as("q=%s", q).contains(findSeedWorkId("월광", MOONLIGHT));
        }

        @Test
        @DisplayName("in=TITLE 로 \"엘리제\"·\"Für Elise\" → 엘리제를 위하여")
        void title_furElise() throws Exception {
            long expected = findSeedWorkId("엘리제를 위하여", FUR_ELISE);
            assertThat(ids(search("엘리제", "TITLE"))).contains(expected);
            assertThat(ids(search("Für Elise", "TITLE"))).contains(expected);
        }

        @ParameterizedTest(name = "in=TITLE, q=\"{0}\" → 작품번호로 찾힌다")
        @DisplayName("작품번호는 \"곡명\"에 들어간다 (기획 04 §4-2 확정, 인수 조건 8-D 4)")
        @ValueSource(strings = {"Op.27 No.2", "op 27 no 2", "op27no2"})
        void title_catalogNumberVariants(String q) throws Exception {
            assertThat(ids(search(q, "TITLE"))).as("q=%s", q).contains(findSeedWorkId("월광", MOONLIGHT));
        }

        @ParameterizedTest(name = "in=TITLE, q=\"{0}\" → 결과가 있다 (작품번호)")
        @ValueSource(strings = {"BWV 846", "BWV846", "K.545", "K545"})
        void title_otherCatalogNumbers(String q) throws Exception {
            assertThat(ids(search(q, "TITLE"))).as("q=%s", q).isNotEmpty();
        }

        @Test
        @DisplayName("작곡가 원어 표기는 곡명 칸에 없다: in=TITLE&q=Chopin → 0건 (인수 조건 8-D 5)")
        void title_doesNotMatchComposerName() throws Exception {
            assertThat(ids(search("Chopin", "TITLE"))).isEmpty();
            assertThat(ids(search("Chopin", "ALL"))).isNotEmpty();
            assertThat(ids(search("Chopin", "COMPOSER"))).isNotEmpty();
        }

        @Test
        @DisplayName("in=TITLE 이면 작곡가 일치 카드를 내리지 않는다 (designer 결정 — 화면정의 02, 인수 조건 8-D 5)")
        void title_hasNoComposerCards() throws Exception {
            JsonNode data = search("Chopin", "TITLE");
            assertThat(data.path("composers").size()).isZero();
            assertThat(data.path("composerMatchCount").asInt()).isZero();

            assertThat(search("Chopin", "ALL").path("composerMatchCount").asInt()).isPositive();
        }

        @Test
        @DisplayName("in=TITLE 에서도 matchedAlias 는 그대로 계산된다 (\"월광\" → \"월광\")")
        void title_keepsMatchedAlias() throws Exception {
            JsonNode content = search("월광", "TITLE").path("works").path("content");
            assertThat(content.get(0).path("matchedAlias").asText()).isEqualTo("월광");
        }
    }

    // ===== 3. COMPOSER — 작곡가 한글·원어·별칭 =====

    @Nested
    @DisplayName("in=COMPOSER — 작곡가 한글·원어·별칭에서만 찾는다")
    class ComposerScope {

        @ParameterizedTest(name = "in=COMPOSER, q=\"{0}\" → 그 작곡가의 곡")
        @DisplayName("작곡가 한글·원어·별칭 (인수 조건 8-D 6)")
        @ValueSource(strings = {"쇼팽", "Chopin", "차이콥스키", "차이코프스키", "Beethoven, Ludwig van"})
        void composer_variants(String q) throws Exception {
            assertThat(ids(search(q, "COMPOSER"))).as("q=%s", q).isNotEmpty();
        }

        @Test
        @DisplayName("\"쇼팽\"과 \"Chopin\" 은 같은 곡들을 낸다 (in=COMPOSER)")
        void composer_koreanAndOriginalAreSame() throws Exception {
            assertThat(ids(search("쇼팽", "COMPOSER"))).isEqualTo(ids(search("Chopin", "COMPOSER")));
        }

        @ParameterizedTest(name = "in=COMPOSER, q=\"{0}\" → 0건")
        @DisplayName("곡 제목·작품번호는 작곡가 칸에 없다 (인수 조건 8-D 7)")
        @ValueSource(strings = {"녹턴", "Op.27 No.2", "월광", "K.545"})
        void composer_doesNotMatchTitleOrCatalog(String q) throws Exception {
            assertThat(ids(search(q, "COMPOSER"))).as("q=%s", q).isEmpty();
        }

        @Test
        @DisplayName("in=COMPOSER 면 matchedAlias 는 항상 null (곡 별칭이 검색 대상이 아니다)")
        void composer_matchedAliasIsAlwaysNull() throws Exception {
            JsonNode content = search("쇼팽", "COMPOSER").path("works").path("content");
            assertThat(content).isNotEmpty();
            for (JsonNode item : content) {
                assertThat(item.path("matchedAlias").isNull()).as("id=%s", item.path("id")).isTrue();
            }
        }

        @Test
        @DisplayName("in=COMPOSER 면 작곡가 일치 카드는 그대로 나온다")
        void composer_keepsComposerCards() throws Exception {
            JsonNode data = search("베토벤", "COMPOSER");
            assertThat(data.path("composerMatchCount").asInt()).isEqualTo(1);
            assertThat(data.path("composers").get(0).path("nameKo").asText()).isEqualTo("베토벤");
        }
    }

    // ===== 4. 여러 단어 AND 는 기준 안에서 그대로 =====

    @Nested
    @DisplayName("여러 단어 AND 규칙은 기준 안에서 그대로 유지된다 (기획 04 §4-2)")
    class AndRuleWithinScope {

        /**
         * AND 의 정의를 그대로 단언한다: <b>두 단어 결과 = 각 단어 결과의 교집합</b>.
         * 시드 문구에 기대지 않으므로 큐레이션이 바뀌어도 규칙만 지켜지면 초록이다.
         */
        @ParameterizedTest(name = "in={0}: \"쇼팽 녹턴\" 결과 = \"쇼팽\" 결과 ∩ \"녹턴\" 결과")
        @ValueSource(strings = {"ALL", "TITLE", "COMPOSER"})
        void twoWords_isIntersectionOfEachWord(String in) throws Exception {
            Set<Long> both = new LinkedHashSet<>(ids(search("쇼팽 녹턴", in)));
            Set<Long> first = new LinkedHashSet<>(ids(search("쇼팽", in)));
            Set<Long> second = new LinkedHashSet<>(ids(search("녹턴", in)));
            first.retainAll(second);
            assertThat(both).as("in=%s", in).isEqualTo(first);
        }

        /**
         * 기획 04 인수 조건 8-D 8 은 {@code in=TITLE, q="쇼팽 녹턴"} 을 <b>0건</b>으로 적었다.
         * 우리 시드는 녹턴 곡의 <b>별칭</b>에 "쇼팽 녹턴" 을 넣어 두었고(works.csv seq 23·26),
         * 별칭은 계약상 <b>곡명 칸</b>이다(§3-1 표) — 그래서 실데이터에서는 0건이 아니다.
         * <b>계약이 옳고 인수 조건의 기대값이 우리 데이터와 어긋난다</b>(02 §8 되돌림).
         * 여기서는 "작곡가 표기로는 곡명에서 못 찾는다" 를 원어 표기로 대신 단언한다.
         */
        @Test
        @DisplayName("in=TITLE, q=\"Chopin 녹턴\" → 0건 (한 단어라도 곡명 칸에 없으면 걸리지 않는다)")
        void title_twoWords_zeroWhenOneWordIsComposerOnly() throws Exception {
            assertThat(ids(search("Chopin 녹턴", "TITLE"))).isEmpty();
            assertThat(ids(search("Chopin 녹턴", "ALL"))).isNotEmpty();
        }
    }

    // ===== 5. totalInAll — 0건 화면 [B] 판정 재료 =====

    @Nested
    @DisplayName("totalInAll — 화면이 \"모름\"과 \"0건\"을 구분할 수 있어야 한다 (화면정의 08 §4-7)")
    class TotalInAll {

        @Test
        @DisplayName("in=ALL 이면 null (모름이 아니라 \"질문이 성립하지 않음\")")
        void allScope_isNull() throws Exception {
            assertThat(search("녹턴", "ALL").path("totalInAll").isNull()).isTrue();
        }

        @Test
        @DisplayName("기준으로 0건이지만 전체로는 결과가 있으면 양수 → 화면 [B]")
        void positiveWhenAllScopeHasResults() throws Exception {
            JsonNode data = search("녹턴", "COMPOSER");
            assertThat(data.path("works").path("totalElements").asLong()).isZero();
            assertThat(data.path("totalInAll").asLong())
                    .isEqualTo(search("녹턴", "ALL").path("works").path("totalElements").asLong())
                    .isPositive();
        }

        @Test
        @DisplayName("전체로도 0건이면 0 → 화면 [C] (null 과 다른 값이어야 한다)")
        void zeroWhenAllScopeAlsoEmpty() throws Exception {
            JsonNode data = search("ㅁㄴㅇㄹㅎㅋ", "TITLE");
            assertThat(data.path("works").path("totalElements").asLong()).isZero();
            assertThat(data.has("totalInAll")).as("키가 아예 없으면 화면이 \"모름\"과 구분할 수 없다").isTrue();
            assertThat(data.path("totalInAll").isNumber()).isTrue();
            assertThat(data.path("totalInAll").asLong()).isZero();
        }

        @Test
        @DisplayName("필터와 무관하다 — [B] 는 필터를 다 푼 뒤의 화면이기 때문 (기획 04 §4-4)")
        void independentOfFilters() throws Exception {
            long withoutFilter = search("녹턴", "COMPOSER").path("totalInAll").asLong();
            MvcResult result = mockMvc.perform(get("/api/works/search")
                            .param("q", "녹턴").param("in", "COMPOSER")
                            .param("level", "ADVANCED").param("downloadable", "true"))
                    .andExpect(status().isOk())
                    .andReturn();
            JsonNode filtered = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
            assertThat(filtered.path("totalInAll").isNumber()).isTrue();
            assertThat(filtered.path("totalInAll").asLong()).isEqualTo(withoutFilter);
        }
    }

    // ===== 5-1. 화면이 보여주는 예시는 0건이 아니어야 한다 (인수 조건 8-E 9) =====

    /**
     * 화면정의 08 §4-3(자리 문구)·§4-5(예시 칩)가 기준마다 예시를 바꾼 이유가 바로 이것이다 —
     * <b>화면이 스스로 판 함정</b>(기준 "곡명" 에서 "쇼팽 녹턴" 칩)을 만들지 않기 위해서다.
     * 그 예시들이 실제 데이터에서 0건이 아닌지는 <b>계약이 아니라 데이터</b>의 문제라 여기서 지킨다.
     */
    @ParameterizedTest(name = "in={0}, 화면 예시 \"{1}\" → 1건 이상")
    @DisplayName("자리 문구·예시 칩이 보여주는 검색어는 그 기준에서 결과가 있다 (인수 조건 8-E 9)")
    @CsvSource({
            "ALL,월광", "ALL,쇼팽 녹턴", "ALL,K.545",
            "TITLE,월광", "TITLE,엘리제를 위하여", "TITLE,Op.27 No.2",
            "COMPOSER,쇼팽", "COMPOSER,베토벤", "COMPOSER,모차르트", "COMPOSER,Chopin"
    })
    void screenExamples_areNeverEmpty(String in, String q) throws Exception {
        assertThat(ids(search(q, in))).as("in=%s q=%s", in, q).isNotEmpty();
    }

    // ===== 6. 기준을 바꿔도 나머지 계약은 그대로 =====

    @Test
    @DisplayName("in=TITLE 에서도 필터·페이지·unfilteredTotal 계약이 그대로다")
    void title_keepsFilterAndPageContract() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/works/search")
                        .param("q", "녹턴").param("in", "TITLE").param("level", "INTERMEDIATE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.works.page").value(0))
                .andExpect(jsonPath("$.data.works.size").value(20))
                .andReturn();
        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
        long unfiltered = search("녹턴", "TITLE").path("works").path("totalElements").asLong();
        assertThat(data.path("unfilteredTotal").asLong()).isEqualTo(unfiltered);
        assertThat(data.path("works").path("totalElements").asLong()).isLessThanOrEqualTo(unfiltered);
    }

    // ===== 헬퍼 =====

    private JsonNode search(String q, String in) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/works/search").param("q", q).param("in", in))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
    }

    private List<Long> ids(JsonNode data) {
        List<Long> ids = new ArrayList<>();
        for (JsonNode item : data.path("works").path("content")) {
            ids.add(item.path("id").asLong());
        }
        return ids;
    }
}
