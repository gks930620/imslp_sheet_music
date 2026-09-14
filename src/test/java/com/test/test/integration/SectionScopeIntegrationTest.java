package com.test.test.integration;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 악기 구분 {@code section} 이 공개 API 6개에 실제로 걸리는가 — docs/설계/02_API_명세서.md §0-7, 기획 04 §5·§8-C.
 *
 * <p><b>이 테스트가 왜 리포지토리(네이티브 SQL)로 given 을 만드는가.</b>
 * 1차에는 곡의 구분을 바꾸는 경로가 <b>설계상 하나도 없다</b> — 관리 API 에도 넣지 않았고(기획 04 §3-6·§9 8-7),
 * 수집도 {@code NOT_PIANO_SOLO} 를 구분으로 바꾸지 않는다(기획 04 §2-2). 그래서 모든 곡이 {@code PIANO} 이고,
 * <b>필터를 구현하지 않아도 "section=PIANO 로 부르면 다 나온다" 는 초록이 된다</b>(02 §0-7 이 적어 둔 위험).
 * 다른 구분의 곡을 한 줄 심어야만 "걸러진다" 를 증명할 수 있으므로, <b>given 단계만</b> 컬럼에 직접 쓴다.
 * 검증은 그대로 MockMvc 상태코드 + 응답 JSON 이다(컨벤션 §6).
 */
class SectionScopeIntegrationTest extends SheetMusicFixtureSupport {

    private static final String TAG = "구분회귀시험곡";

    @PersistenceContext
    private EntityManager entityManager;

    // ===== §3-1 검색 =====

    @Nested
    @DisplayName("§3-1 검색")
    class Search {

        @Test
        @DisplayName("section 을 생략하면 PIANO 다 — 다른 구분의 곡은 나오지 않는다 (인수 조건 8-C 1)")
        void omittedSection_isPiano() throws Exception {
            Fixture fixture = givenOnePianoAndOneViolinWork();

            assertThat(searchIds(TAG, null))
                    .contains(fixture.pianoWorkId())
                    .doesNotContain(fixture.violinWorkId());
        }

        @Test
        @DisplayName("section=PIANO 는 생략과 같은 결과다 (옛 링크 회귀 — 기획 04 §3-5)")
        void explicitPiano_equalsOmitted() throws Exception {
            givenOnePianoAndOneViolinWork();

            assertThat(searchIds(TAG, "PIANO")).isEqualTo(searchIds(TAG, null));
        }

        @Test
        @DisplayName("section=VIOLIN 은 바이올린 곡만 낸다 — 구분을 넘어 찾지 않는다 (기획 04 §5)")
        void violinSection_returnsOnlyViolinWork() throws Exception {
            Fixture fixture = givenOnePianoAndOneViolinWork();

            assertThat(searchIds(TAG, "VIOLIN")).containsExactly(fixture.violinWorkId());
        }

        @ParameterizedTest(name = "section={0} 는 대소문자를 무시한다")
        @ValueSource(strings = {"piano", "Piano", "PIANO"})
        void section_isCaseInsensitive(String raw) throws Exception {
            Fixture fixture = givenOnePianoAndOneViolinWork();

            assertThat(searchIds(TAG, raw)).contains(fixture.pianoWorkId());
        }

        @ParameterizedTest(name = "section={0} → 400 (검색 기준 in 의 관용 처리와 일부러 다르다 — §0-7)")
        @ValueSource(strings = {"CELLO", "xyz", "PIANO_SOLO"})
        void unknownSection_is400(String raw) throws Exception {
            mockMvc.perform(get("/api/works/search").param("q", "월광").param("section", raw))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.errorCode").value("BUSINESS_RULE_VIOLATION"));
        }

        @Test
        @DisplayName("unfilteredTotal·totalInAll·작곡가 카드도 그 구분 안에서 센다")
        void totalsAndComposerCards_areScoped() throws Exception {
            Fixture fixture = givenOnePianoAndOneViolinWork();

            JsonNode piano = search(TAG, "PIANO", null);
            assertThat(piano.path("unfilteredTotal").asLong())
                    .isEqualTo(piano.path("works").path("totalElements").asLong());

            // 바이올린 전용 작곡가는 피아노 구분의 작곡가 카드에 나오지 않는다
            JsonNode cards = search(fixture.violinComposerName(), "PIANO", null).path("composers");
            assertThat(cards).isEmpty();
            assertThat(search(fixture.violinComposerName(), "VIOLIN", null).path("composers")).isNotEmpty();
        }
    }

    // ===== §3-2 인기곡 =====

    @Test
    @DisplayName("§3-2 인기곡: 자격 곡·폴백 곡 모두 그 구분 안에서만 고른다")
    void popular_isScopedBySection() throws Exception {
        Tokens admin = loginAdmin();
        long pianoComposer = createComposer(admin, "구분피아노", "Section Piano Composer");
        long violinComposer = createComposer(admin, "구분바이올린", "Section Violin Composer");
        long pianoWorkId = createReadyWork(admin, pianoComposer, TAG + " 피아노", "Section Piano Work", 10).workId();
        long violinWorkId = createReadyWork(admin, violinComposer, TAG + " 바이올린", "Section Violin Work", 10).workId();
        moveToSection(violinWorkId, "VIOLIN");

        assertThat(popularIds("PIANO")).contains(pianoWorkId).doesNotContain(violinWorkId);
        assertThat(popularIds("VIOLIN")).containsExactly(violinWorkId);
        assertThat(popularIds(null)).isEqualTo(popularIds("PIANO"));
    }

    // ===== §3-5 ~ §3-8 작곡가 =====

    @Nested
    @DisplayName("§3-5 ~ §3-8 작곡가 — \"공개 곡 1개 이상\" 의 뜻이 그 구분 기준으로 좁아진다")
    class Composers {

        @Test
        @DisplayName("§3-5 작곡가 목록: 바이올린 곡만 있는 작곡가는 피아노 구분 목록에 없다 (기획 04 §5)")
        void composerList_isScoped() throws Exception {
            Fixture fixture = givenOnePianoAndOneViolinWork();

            assertThat(composerIds("/api/composers", "PIANO"))
                    .contains(fixture.pianoComposerId())
                    .doesNotContain(fixture.violinComposerId());
            assertThat(composerIds("/api/composers", "VIOLIN"))
                    .containsExactly(fixture.violinComposerId());
        }

        @Test
        @DisplayName("§3-6 featured 도 같은 규칙")
        void featured_isScoped() throws Exception {
            Fixture fixture = givenOnePianoAndOneViolinWork();

            assertThat(composerIds("/api/composers/featured?limit=20", "VIOLIN"))
                    .containsExactly(fixture.violinComposerId());
        }

        @Test
        @DisplayName("§3-7 작곡가 상세: workCount 만 구분 기준이고 404 조건은 바뀌지 않는다 (곡 0개여도 200)")
        void composerDetail_countIsScopedButStatusIsNot() throws Exception {
            Fixture fixture = givenOnePianoAndOneViolinWork();

            mockMvc.perform(get("/api/composers/{id}", fixture.violinComposerId()).param("section", "PIANO"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.workCount").value(0));
            mockMvc.perform(get("/api/composers/{id}", fixture.violinComposerId()).param("section", "VIOLIN"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.workCount").value(1));
        }

        @Test
        @DisplayName("§3-8 작곡가의 곡: 같은 작곡가라도 구분마다 다른 곡 목록을 갖는다")
        void composerWorks_isScoped() throws Exception {
            Fixture fixture = givenOnePianoAndOneViolinWork();

            mockMvc.perform(get("/api/composers/{id}/works", fixture.violinComposerId()).param("section", "PIANO"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.works.totalElements").value(0))
                    .andExpect(jsonPath("$.data.unfilteredTotal").value(0));
            mockMvc.perform(get("/api/composers/{id}/works", fixture.violinComposerId()).param("section", "VIOLIN"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.works.totalElements").value(1))
                    .andExpect(jsonPath("$.data.works.content[0].id").value(fixture.violinWorkId()));
        }
    }

    // ===== §3-3 곡 상세 =====

    @Nested
    @DisplayName("§3-3 곡 상세 — 곡이 스스로 구분을 안다")
    class WorkDetail {

        @Test
        @DisplayName("응답에 section 이 있다 (화면이 다른 구분의 곡 주소를 그 구분으로 전환하는 근거 — 기획 04 §1-4)")
        void detail_carriesSection() throws Exception {
            Fixture fixture = givenOnePianoAndOneViolinWork();

            mockMvc.perform(get("/api/works/{id}", fixture.pianoWorkId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.section").value("PIANO"));
            mockMvc.perform(get("/api/works/{id}", fixture.violinWorkId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.section").value("VIOLIN"));
        }

        @Test
        @DisplayName("곡 상세는 section 파라미터를 받지 않는다 — 이상한 값을 붙여도 200 이고 결과가 같다")
        void detail_ignoresSectionParam() throws Exception {
            Fixture fixture = givenOnePianoAndOneViolinWork();

            mockMvc.perform(get("/api/works/{id}", fixture.violinWorkId()).param("section", "PIANO"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.section").value("VIOLIN"));
        }

        @Test
        @DisplayName("\"같은 작곡가의 다른 곡\" 도 그 곡의 구분 안에서만 고른다 (기획 04 §5)")
        void sameComposerWorks_areScoped() throws Exception {
            Tokens admin = loginAdmin();
            long composerId = createComposer(admin, "구분혼합", "Section Mixed Composer");
            long pianoWorkId = createWork(admin, composerId, TAG + " 피아노", "Mixed Piano Work");
            long anotherPianoWorkId = createWork(admin, composerId, TAG + " 피아노2", "Mixed Piano Work 2");
            long violinWorkId = createWork(admin, composerId, TAG + " 바이올린", "Mixed Violin Work");
            moveToSection(violinWorkId, "VIOLIN");

            List<Long> sameComposer = new ArrayList<>();
            JsonNode detail = getJson("/api/works/{id}", pianoWorkId).path("data");
            for (JsonNode item : detail.path("sameComposerWorks")) {
                sameComposer.add(item.path("id").asLong());
            }
            assertThat(sameComposer).contains(anotherPianoWorkId).doesNotContain(violinWorkId);
        }
    }

    // ===== 관리 API 는 구분 밖 =====

    @Test
    @DisplayName("관리 곡 목록은 구분이 걸리지 않는다 — 관리자는 모든 구분을 한 화면에서 본다 (기획 04 §3-6)")
    void adminWorkList_isNotScoped() throws Exception {
        Fixture fixture = givenOnePianoAndOneViolinWork();
        Tokens admin = loginAdmin();

        JsonNode works = getJsonAsAdmin(admin, "/api/admin/works?q=" + TAG).path("data").path("works").path("content");
        List<Long> ids = new ArrayList<>();
        for (JsonNode item : works) {
            ids.add(item.path("id").asLong());
        }
        assertThat(ids).contains(fixture.pianoWorkId(), fixture.violinWorkId());
    }

    // ===== given / 헬퍼 =====

    private record Fixture(long pianoComposerId, long violinComposerId, String violinComposerName,
                           long pianoWorkId, long violinWorkId) {
    }

    private Fixture givenOnePianoAndOneViolinWork() throws Exception {
        Tokens admin = loginAdmin();
        String violinComposerName = "구분바이올린작곡가";
        long pianoComposerId = createComposer(admin, "구분피아노작곡가", "Section Piano Composer");
        long violinComposerId = createComposer(admin, violinComposerName, "Section Violin Composer");
        long pianoWorkId = createWork(admin, pianoComposerId, TAG + " 피아노", "Section Piano Work");
        long violinWorkId = createWork(admin, violinComposerId, TAG + " 바이올린", "Section Violin Work");
        moveToSection(violinWorkId, "VIOLIN");
        return new Fixture(pianoComposerId, violinComposerId, violinComposerName, pianoWorkId, violinWorkId);
    }

    /** 02 §0-7 — 1차에 이 값을 바꾸는 API 가 없으므로 given 만 컬럼에 직접 쓴다. */
    private void moveToSection(long workId, String section) {
        entityManager.flush();
        entityManager.createNativeQuery("UPDATE work SET section = :section WHERE id = :id")
                .setParameter("section", section)
                .setParameter("id", workId)
                .executeUpdate();
        entityManager.clear();
    }

    private JsonNode search(String q, String section, String in) throws Exception {
        var request = get("/api/works/search").param("q", q);
        if (section != null) {
            request = request.param("section", section);
        }
        if (in != null) {
            request = request.param("in", in);
        }
        MvcResult result = mockMvc.perform(request).andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
    }

    private List<Long> searchIds(String q, String section) throws Exception {
        List<Long> ids = new ArrayList<>();
        for (JsonNode item : search(q, section, null).path("works").path("content")) {
            ids.add(item.path("id").asLong());
        }
        return ids;
    }

    private List<Long> popularIds(String section) throws Exception {
        var request = get("/api/works/popular").param("limit", "20");
        if (section != null) {
            request = request.param("section", section);
        }
        MvcResult result = mockMvc.perform(request).andExpect(status().isOk()).andReturn();
        List<Long> ids = new ArrayList<>();
        for (JsonNode item : objectMapper.readTree(result.getResponse().getContentAsString()).path("data")) {
            ids.add(item.path("id").asLong());
        }
        return ids;
    }

    private List<Long> composerIds(String url, String section) throws Exception {
        String separator = url.contains("?") ? "&" : "?";
        MvcResult result = mockMvc.perform(get(url + separator + "section=" + section))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
        JsonNode list = data.isArray() ? data : data.path("composers");
        List<Long> ids = new ArrayList<>();
        for (JsonNode item : list) {
            ids.add(item.path("id").asLong());
        }
        return ids;
    }
}
