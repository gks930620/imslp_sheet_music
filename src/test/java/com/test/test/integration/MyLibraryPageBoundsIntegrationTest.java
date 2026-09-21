package com.test.test.integration;

import com.test.test.integration.support.MemberApiTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.ResultActions;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 내 악보 두 탭의 <b>페이지 범위 밖</b> (02 §10-2 · §10-3) — qa 8차 결함 2.
 *
 * <p>계약은 "200 + 빈 {@code content}, {@code totalElements} 는 그대로" 다. 그런데 이 두 탭만
 * {@code page} 가 크면 500 이다 — 문자열 {@code @Query} 로 페이지를 읽으면 스프링 데이터가
 * offset 을 {@code int} 로 좁히기 때문이다({@code PageableUtils.getOffsetAsInteger} →
 * {@code InvalidDataAccessApiUsageException}). 같은 값으로 {@code /api/works/search}·
 * {@code /api/composers/{id}/works}·{@code /api/admin/works} 는 전부 200 이라, <b>같은 계약이
 * 목록마다 다른 답을 주는</b> 상태다.
 *
 * <p>왜 "빈 목록" 이 맞는 답인가: 페이지 번호는 <b>주소창에 그대로 드러나는 값</b>이라 사용자가 손으로
 * 고치고 링크로 공유한다. 범위 밖 페이지는 "없는 리소스" 가 아니라 "그 페이지에 아무것도 없는 상태" 이고
 * (화면정의 09 상태표가 그 화면을 이미 정의하고 있다), 400/404 로 바꾸면 다른 목록과 답이 갈린다.
 *
 * <p>{@code page} 는 <b>요청한 값을 그대로 되비춘다</b> — 서버가 몰래 다른 페이지로 옮기면 화면의
 * 페이지 이동이 "눌러도 그 자리" 가 된다. {@code counts} 와 {@code totalElements} 는 페이지와 무관하므로
 * 범위 밖에서도 실제 수를 말한다(§10-2).
 *
 * <p><b>{@code last} 는 {@code page = Integer.MAX_VALUE} 에서 단언하지 않는다</b>: 스프링 데이터의
 * {@code PageImpl.hasNext()} 가 {@code getNumber() + 1} 로 판정해 그 값에서 int 가 넘친다 —
 * 모든 목록 API 가 똑같이 그렇고(검색·작곡가 곡·관리 목록), 이번에 바로잡는 것은 <b>500</b> 이다.
 * 넘치지 않는 경계 페이지에서는 {@code last} 까지 본다(세 번째 테스트).
 */
class MyLibraryPageBoundsIntegrationTest extends MemberApiTestSupport {

    /** §10-0 기본 size. 화면은 {@code size} 를 보내지 않으므로 경계는 이 값으로 정해진다. */
    private static final int DEFAULT_SIZE = 20;

    /** offset({@code page * size})이 {@code int} 를 넘는 <b>첫</b> 페이지 = 107,374,183 (qa 실측 경계). */
    private static final int FIRST_PAGE_OVER_INT_OFFSET = Integer.MAX_VALUE / DEFAULT_SIZE + 1;

    @Test
    @DisplayName("즐겨찾기 탭: page 가 아무리 커도 200 + 빈 목록 — counts·totalElements 는 그대로")
    void favoritesFarPageIsEmptyNotError() throws Exception {
        Tokens admin = loginAdmin();
        Tokens member = loginMember();
        givenOneFavoriteAndOneDownload(admin, member);

        favorites(member, Integer.MAX_VALUE)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.works.content").isArray())
                .andExpect(jsonPath("$.data.works.content").isEmpty())
                .andExpect(jsonPath("$.data.works.page").value(Integer.MAX_VALUE))
                .andExpect(jsonPath("$.data.works.size").value(DEFAULT_SIZE))
                .andExpect(jsonPath("$.data.works.totalElements").value(1))
                .andExpect(jsonPath("$.data.works.totalPages").value(1))
                .andExpect(jsonPath("$.data.works.first").value(false))
                .andExpect(jsonPath("$.data.counts.favorites").value(1))
                .andExpect(jsonPath("$.data.counts.downloads").value(1));
    }

    @Test
    @DisplayName("받은 악보 탭: page 가 아무리 커도 200 + 빈 목록 — counts·totalElements 는 그대로")
    void downloadsFarPageIsEmptyNotError() throws Exception {
        Tokens admin = loginAdmin();
        Tokens member = loginMember();
        givenOneFavoriteAndOneDownload(admin, member);

        downloads(member, Integer.MAX_VALUE)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.content").isArray())
                .andExpect(jsonPath("$.data.items.content").isEmpty())
                .andExpect(jsonPath("$.data.items.page").value(Integer.MAX_VALUE))
                .andExpect(jsonPath("$.data.items.size").value(DEFAULT_SIZE))
                .andExpect(jsonPath("$.data.items.totalElements").value(1))
                .andExpect(jsonPath("$.data.items.totalPages").value(1))
                .andExpect(jsonPath("$.data.items.first").value(false))
                .andExpect(jsonPath("$.data.counts.favorites").value(1))
                .andExpect(jsonPath("$.data.counts.downloads").value(1));
    }

    @Test
    @DisplayName("경계: offset 이 int 를 넘는 첫 페이지에서도 두 탭 모두 200 — 그 앞 페이지와 답이 같다")
    void bothTabsAgreeAcrossTheIntOffsetBoundary() throws Exception {
        Tokens admin = loginAdmin();
        Tokens member = loginMember();
        givenOneFavoriteAndOneDownload(admin, member);

        // 경계 바로 앞 — 지금도 200 이다(회귀 대조)
        favorites(member, FIRST_PAGE_OVER_INT_OFFSET - 1)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.works.content").isEmpty());
        downloads(member, FIRST_PAGE_OVER_INT_OFFSET - 1)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.content").isEmpty());

        // 한 페이지 넘어갔다고 답이 200 → 500 으로 바뀌지 않는다
        favorites(member, FIRST_PAGE_OVER_INT_OFFSET)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.works.content").isEmpty())
                .andExpect(jsonPath("$.data.works.page").value(FIRST_PAGE_OVER_INT_OFFSET))
                .andExpect(jsonPath("$.data.works.totalElements").value(1))
                .andExpect(jsonPath("$.data.works.last").value(true));
        downloads(member, FIRST_PAGE_OVER_INT_OFFSET)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.content").isEmpty())
                .andExpect(jsonPath("$.data.items.page").value(FIRST_PAGE_OVER_INT_OFFSET))
                .andExpect(jsonPath("$.data.items.totalElements").value(1))
                .andExpect(jsonPath("$.data.items.last").value(true));
    }

    /** 두 탭에 각각 한 건씩 — 범위 밖 페이지에서도 {@code counts}·{@code totalElements} 가 살아 있는지 보려면 0 이면 안 된다. */
    private void givenOneFavoriteAndOneDownload(Tokens admin, Tokens member) throws Exception {
        long composerId = createComposer(admin);
        long favoriteWorkId = createWork(admin, composerId);
        long downloadWorkId = createWork(admin, composerId);
        givenFavorite(member, favoriteWorkId);
        downloadAs(member, makeReady(admin, downloadWorkId)).andExpect(status().isOk());
    }

    private ResultActions favorites(Tokens member, int page) throws Exception {
        return libraryTab(member, "/api/me/library/favorites", page);
    }

    private ResultActions downloads(Tokens member, int page) throws Exception {
        return libraryTab(member, "/api/me/library/downloads", page);
    }

    private ResultActions libraryTab(Tokens member, String url, int page) throws Exception {
        return mockMvc.perform(get(url)
                .param("section", "PIANO")
                .param("page", String.valueOf(page))
                .header(HttpHeaders.AUTHORIZATION, bearer(member.accessToken())));
    }
}
