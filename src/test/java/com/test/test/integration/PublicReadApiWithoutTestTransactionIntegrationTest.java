package com.test.test.integration;

import com.test.test.integration.support.NonTransactionalApiTestSupport;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * <b>테스트 트랜잭션 없이</b> 도는 주요 GET — 운영과 같은 조건({@code open-in-view: false}, 요청마다 새 영속성 컨텍스트).
 *
 * <p>왜 따로 두는가: 기존 통합테스트는 테스트 스레드에 영속성 컨텍스트가 열린 채 컨트롤러가 돌아서
 * lazy 프록시·detach 결함을 못 잡는다(qa 결함 D2 — 커뮤니티 상세가 운영에서 전건 500 인데 테스트는 초록).
 * 이 클래스는 <b>사용자에게 노출되는 대표 GET</b> 을 그 안전망 없이 한 번씩 통과시키는 회귀 가드다.
 * 응답 필드를 여기서 전부 다시 검증하지는 않는다(그건 각 API 의 기존 테스트 몫) —
 * <b>200 이 나오는가</b> 와 <b>연관 엔티티에서 온 필드가 채워지는가</b>(= 세션이 필요한 지점)만 본다.
 *
 * <p>이름은 "Public" 이지만 <b>로그인해야 보이는 조회</b>(내 악보 §10-2·§10-3)도 여기 든다(2026-09-21) —
 * 가드가 막는 것은 "공개인가" 가 아니라 "열린 세션에 기대는가" 이고, 그 위험은 인증 여부와 무관하다.
 * 클래스를 하나 더 만들면 §14 의 "새 조회 API 는 한 줄 추가" 가 두 곳으로 갈린다.
 *
 * @see NonTransactionalApiTestSupport
 */
class PublicReadApiWithoutTestTransactionIntegrationTest extends NonTransactionalApiTestSupport {

    // ===== 커뮤니티 (qa 결함 D2) =====

    @Test
    @DisplayName("GET /api/communities/{id} — 200 이고 작성자(lazy user)가 채워진다")
    void communityDetail_worksWithoutOpenSession() throws Exception {
        mockMvc.perform(get("/api/communities/{id}", 1L).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.userId").isNumber())
                .andExpect(jsonPath("$.data.username").isString())
                .andExpect(jsonPath("$.data.nickname").isString());
    }

    @Test
    @DisplayName("GET /api/communities/{id}/comments — 200 이고 각 댓글의 작성자(lazy user)가 채워진다")
    void commentList_worksWithoutOpenSession() throws Exception {
        mockMvc.perform(get("/api/communities/{id}/comments", 1L)
                        .param("page", "0")
                        .param("size", "5")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.totalElements", greaterThan(0)))
                .andExpect(jsonPath("$.data.content[0].username").isString())
                .andExpect(jsonPath("$.data.content[0].nickname").isString());
    }

    // ===== 쉬운악보 공개 API (신규 — 회귀 가드) =====

    @Test
    @DisplayName("GET /api/works/{id} — 추천 판본·다른 판본·작곡가·같은 작곡가 곡이 세션 없이 직렬화된다")
    void workDetail_worksWithoutOpenSession() throws Exception {
        Tokens admin = loginAdmin();
        long composerId = createComposer(admin, "비트랜잭션작곡가", "Nontx, Composer");
        ReadyWork ready = createWorkWithRecommendedEdition(admin, composerId, "비트랜잭션 소나타", "Nontx Sonata",
                List.of("Op.1"), List.of("비트랜잭션별칭"), "INTERMEDIATE", 2, "FREE");
        createWork(admin, composerId, "비트랜잭션 다른곡", "Nontx Other");

        mockMvc.perform(get("/api/works/{id}", ready.workId()).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value((int) ready.workId()))
                .andExpect(jsonPath("$.data.composer.nameKo").value("비트랜잭션작곡가"))
                .andExpect(jsonPath("$.data.aliases[0]").value("비트랜잭션별칭"))
                .andExpect(jsonPath("$.data.catalogNumbers[0]").value("Op.1"))
                .andExpect(jsonPath("$.data.recommendedEdition.id").value((int) ready.editionId()))
                .andExpect(jsonPath("$.data.recommendedEdition.downloadable").value(true))
                .andExpect(jsonPath("$.data.sameComposerWorks").isArray());
    }

    @Test
    @DisplayName("GET /api/works/search — 곡 카드의 작곡가·작품번호가 세션 없이 직렬화된다")
    void workSearch_worksWithoutOpenSession() throws Exception {
        Tokens admin = loginAdmin();
        long composerId = createComposer(admin, "비트랜잭션작곡가", "Nontx, Composer");
        createWorkWithRecommendedEdition(admin, composerId, "비트랜잭션 검색곡", "Nontx Searchable",
                List.of("Op.2"), List.of("비트랜잭션검색별칭"), "INTERMEDIATE", 2, "FREE");

        mockMvc.perform(get("/api/works/search").param("q", "비트랜잭션 검색곡"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.works.totalElements", greaterThan(0)))
                .andExpect(jsonPath("$.data.works.content[0].titleKo").value("비트랜잭션 검색곡"))
                .andExpect(jsonPath("$.data.works.content[0].composer.nameKo").value("비트랜잭션작곡가"))
                .andExpect(jsonPath("$.data.works.content[0].catalogNumbers[0]").value("Op.2"));

        // 별칭으로 찾았을 때의 matchedAlias 도 연관 엔티티에서 온다
        mockMvc.perform(get("/api/works/search").param("q", "비트랜잭션검색별칭"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.works.content[0].matchedAlias").value("비트랜잭션검색별칭"));
    }

    @Test
    @DisplayName("GET /api/works/popular — 200, 곡 카드의 작곡가가 채워진다")
    void workPopular_worksWithoutOpenSession() throws Exception {
        Tokens admin = loginAdmin();
        long composerId = createComposer(admin, "비트랜잭션작곡가", "Nontx, Composer");
        // §3-2(2026-09-08 개정): 자격은 READY + title_ko — 추천 판본 없는 PREPARING 곡만 있으면 빈 배열이라
        // 지연 로딩 직렬화를 볼 수 있는 행 자체가 생기지 않는다
        createWorkWithRecommendedEdition(admin, composerId, "비트랜잭션 인기곡", "Nontx Popular",
                List.of("Op.3"), List.of(), "INTERMEDIATE", 2, "FREE");

        mockMvc.perform(get("/api/works/popular").param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").isNumber())
                .andExpect(jsonPath("$.data[0].composer.nameKo").value("비트랜잭션작곡가"));
    }

    @Test
    @DisplayName("GET /api/composers, /{id}, /{id}/works — 별칭·곡 수·곡 목록이 세션 없이 직렬화된다")
    void composerApis_workWithoutOpenSession() throws Exception {
        Tokens admin = loginAdmin();
        long composerId = createComposer(admin, "비트랜잭션작곡가", "Nontx, Composer");
        createWorkWithRecommendedEdition(admin, composerId, "비트랜잭션 소나타", "Nontx Sonata",
                List.of("Op.1"), List.of(), "INTERMEDIATE", 2, "FREE");

        mockMvc.perform(get("/api/composers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total", greaterThan(0)))
                .andExpect(jsonPath("$.data.composers[0].nameKo").value("비트랜잭션작곡가"))
                .andExpect(jsonPath("$.data.composers[0].workCount").value(1));

        mockMvc.perform(get("/api/composers/{id}", composerId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nameKo").value("비트랜잭션작곡가"))
                .andExpect(jsonPath("$.data.aliases").isArray())
                .andExpect(jsonPath("$.data.workCount").value(1));

        mockMvc.perform(get("/api/composers/{id}/works", composerId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.works.content[0].titleKo").value("비트랜잭션 소나타"))
                .andExpect(jsonPath("$.data.works.content[0].composer.nameKo").value("비트랜잭션작곡가"));

        mockMvc.perform(get("/api/composers/featured").param("limit", "8"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].nameKo").value("비트랜잭션작곡가"));
    }

    // ===== 최근 본 곡 · 내 악보 (2026-09-20 신설 — §14 "새 조회 API 는 여기 한 줄") =====

    @Test
    @DisplayName("GET /api/works/recent — 요청한 순서 그대로, 곡 카드의 작곡가가 세션 없이 채워진다")
    void recentWorks_worksWithoutOpenSession() throws Exception {
        Tokens admin = loginAdmin();
        long composerId = createComposer(admin, "비트랜잭션작곡가", "Nontx, Composer");
        ReadyWork first = createWorkWithRecommendedEdition(admin, composerId, "비트랜잭션 최근곡1", "Nontx Recent 1",
                List.of("Op.4"), List.of(), "INTERMEDIATE", 2, "FREE");
        ReadyWork second = createWorkWithRecommendedEdition(admin, composerId, "비트랜잭션 최근곡2", "Nontx Recent 2",
                List.of("Op.5"), List.of(), "INTERMEDIATE", 2, "FREE");

        mockMvc.perform(get("/api/works/recent")
                        .param("ids", second.workId() + "," + first.workId())
                        .param("section", "PIANO"))
                .andExpect(status().isOk())
                // 브라우저가 든 "최근에 본 순" 을 서버가 다시 정하지 않는다(02 §3-9)
                .andExpect(jsonPath("$.data[0].id").value((int) second.workId()))
                .andExpect(jsonPath("$.data[1].id").value((int) first.workId()))
                .andExpect(jsonPath("$.data[0].composer.nameKo").value("비트랜잭션작곡가"))
                .andExpect(jsonPath("$.data[0].catalogNumbers[0]").value("Op.5"));
    }

    @Test
    @DisplayName("GET /api/me/library/favorites — 즐겨찾기 곡 카드가 세션 없이 직렬화된다")
    void favoriteLibrary_worksWithoutOpenSession() throws Exception {
        Tokens admin = loginAdmin();
        Tokens member = loginUser();
        long composerId = createComposer(admin, "비트랜잭션작곡가", "Nontx, Composer");
        ReadyWork ready = createWorkWithRecommendedEdition(admin, composerId, "비트랜잭션 즐겨찾기곡", "Nontx Favorite",
                List.of("Op.6"), List.of("비트랜잭션즐겨별칭"), "INTERMEDIATE", 2, "FREE");

        mockMvc.perform(put("/api/me/favorites/{workId}", ready.workId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(member.accessToken())))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/me/library/favorites")
                        .param("section", "PIANO")
                        .header(HttpHeaders.AUTHORIZATION, bearer(member.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.counts.favorites").value(1))
                .andExpect(jsonPath("$.data.works.content[0].id").value((int) ready.workId()))
                // 곡 카드는 다른 목록과 한 글자도 다르지 않다(02 §10-2) — 전부 연관 엔티티에서 온다
                .andExpect(jsonPath("$.data.works.content[0].composer.nameKo").value("비트랜잭션작곡가"))
                .andExpect(jsonPath("$.data.works.content[0].catalogNumbers[0]").value("Op.6"));
    }

    @Test
    @DisplayName("GET /api/me/library/downloads — 받은 판본 줄(files 행)까지 세션 없이 만들어진다")
    void downloadLibrary_worksWithoutOpenSession() throws Exception {
        Tokens admin = loginAdmin();
        Tokens member = loginUser();
        long composerId = createComposer(admin, "비트랜잭션작곡가", "Nontx, Composer");
        ReadyWork ready = createWorkWithRecommendedEdition(admin, composerId, "비트랜잭션 받은곡", "Nontx Received",
                List.of("Op.7"), List.of(), "INTERMEDIATE", 2, "FREE");

        // 실제 다운로드로 선반을 만든다 — MyLibraryRecorder 가 운영과 같은 트랜잭션 경계에서 돈다
        mockMvc.perform(get("/api/editions/{id}/download", ready.editionId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(member.accessToken())))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/me/library/downloads")
                        .param("section", "PIANO")
                        .header(HttpHeaders.AUTHORIZATION, bearer(member.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.counts.downloads").value(1))
                .andExpect(jsonPath("$.data.items.content[0].work.id").value((int) ready.workId()))
                .andExpect(jsonPath("$.data.items.content[0].work.composer.nameKo").value("비트랜잭션작곡가"))
                // 받은 판본 줄은 판본(lazy)과 files 행에서 만들어진다 — 열린 세션에 기대면 여기서 터진다
                .andExpect(jsonPath("$.data.items.content[0].receivedEdition.id").value((int) ready.editionId()))
                .andExpect(jsonPath("$.data.items.content[0].receivedEdition.fileSize").isNumber())
                .andExpect(jsonPath("$.data.items.content[0].redownloadState").value("AVAILABLE"))
                .andExpect(jsonPath("$.data.items.content[0].redownloadUrl")
                        .value("/api/editions/" + ready.editionId() + "/download"));
    }

    @Test
    @DisplayName("GET /api/editions/{id}/download — 파일명(작곡가·곡)까지 세션 없이 만들어진다")
    void download_worksWithoutOpenSession() throws Exception {
        Tokens admin = loginAdmin();
        long composerId = createComposer(admin, "비트랜잭션작곡가", "Nontx, Composer");
        ReadyWork ready = createWorkWithRecommendedEdition(admin, composerId, "비트랜잭션 소나타", "Nontx Sonata",
                List.of("Op.1"), List.of(), "INTERMEDIATE", 2, "FREE");

        mockMvc.perform(get("/api/editions/{id}/download", ready.editionId()))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, containsString(MediaType.APPLICATION_PDF_VALUE)))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("attachment")))
                // 파일명은 작곡가·작품번호(연관 엔티티)에서 만들어진다 — 폴백으로 뭉개지면 이름이 비어 보인다
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, not(containsString("filename*=UTF-8''.pdf"))));
    }
}
