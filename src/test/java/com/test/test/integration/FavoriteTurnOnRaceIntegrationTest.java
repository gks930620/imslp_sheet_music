package com.test.test.integration;

import com.test.test.integration.support.NonTransactionalApiTestSupport;
import com.test.test.sheetmusic.member.repository.WorkFavoriteRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 즐겨찾기 켜기의 <b>동시 요청</b> (02 §10-1 멱등) — 리뷰 제안 2.
 *
 * <p>켜기는 "있나 보고(없으면) 넣는다" 라서, 그 사이에 같은 계정의 다른 요청이 끼면 뒤에 온 요청이
 * {@code uk_work_favorite} 을 위반한다. 실제로 겹치는 경로가 있다 — 로그인 복귀 완성(03 §22)이
 * 자동으로 켜는 동안 사용자가 하트를 한 번 더 누르는 경우다. <b>PUT 은 멱등이 계약</b>이므로
 * 그 결과는 500 이 아니라 "이미 켜져 있다(200)" 여야 한다.
 *
 * <p>경합은 스레드로 재현하면 뜨는 날·안 뜨는 날이 갈리므로(컨벤션 §6 "순서/랜덤 의존 테스트 금지"),
 * <b>있나 보는 한 번</b>만 "없다" 로 답하게 해 그 순간을 결정적으로 만든다 — 그 뒤의 조회는 실제 DB 를 본다.
 *
 * <p><b>테스트 트랜잭션이 없어야 한다</b>: 제약 위반은 트랜잭션을 rollback-only 로 만들기 때문에,
 * 테스트가 트랜잭션을 열어 둔 채 돌면 운영에서 무슨 일이 벌어지는지 볼 수 없다.
 */
class FavoriteTurnOnRaceIntegrationTest extends NonTransactionalApiTestSupport {

    @SpyBean
    private WorkFavoriteRepository workFavoriteRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("같은 순간 두 번 켜져도 200 이고, created_at 은 그대로다 — 순서가 흔들리지 않는다")
    void concurrentTurnOn_isIdempotent_andKeepsCreatedAt() throws Exception {
        Tokens admin = loginAdmin();
        Tokens member = loginUser();
        long composerId = createComposer(admin, "경합작곡가", "Race, Composer");
        long firstWork = createWork(admin, composerId, "경합 먼저곡", "Race First");
        long secondWork = createWork(admin, composerId, "경합 나중곡", "Race Second");

        favoriteOn(member, firstWork).andExpect(status().isOk());
        favoriteOn(member, secondWork).andExpect(status().isOk());
        String createdAt = createdAtOf(firstWork);

        // "있나?" 를 한 번만 '없다' 로 답한다 = 그 사이에 다른 요청이 같은 행을 넣어 버린 상태.
        // 그 다음 물음부터는 실제 DB 와 같은 답('있다')을 준다 — 넣기가 깨진 뒤에 다시 확인하는 경로까지 재현한다.
        doReturn(false).doReturn(true)
                .when(workFavoriteRepository).existsByUserIdAndWorkId(anyLong(), eq(firstWork));

        favoriteOn(member, firstWork)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.workId").value(firstWork))
                .andExpect(jsonPath("$.data.favorited").value(true));

        assertThat(rowCountOf(firstWork))
                .as("행이 두 개 생기지 않는다(uk_work_favorite)")
                .isEqualTo(1);
        assertThat(createdAtOf(firstWork))
                .as("다시 누른 것은 '새로 넣은 것' 이 아니다 — created_at 이 갱신되면 '최근에 넣은 순' 이 거짓말이 된다")
                .isEqualTo(createdAt);

        mockMvc.perform(get("/api/me/library/favorites")
                        .param("section", "PIANO")
                        .header(HttpHeaders.AUTHORIZATION, bearer(member.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.counts.favorites").value(2))
                .andExpect(jsonPath("$.data.works.content[0].id").value((int) secondWork))
                .andExpect(jsonPath("$.data.works.content[1].id").value((int) firstWork));
    }

    private ResultActions favoriteOn(Tokens tokens, long workId) throws Exception {
        return mockMvc.perform(put("/api/me/favorites/{workId}", workId)
                .header(HttpHeaders.AUTHORIZATION, bearer(tokens.accessToken())));
    }

    private String createdAtOf(long workId) {
        return jdbcTemplate.queryForObject(
                "select created_at from work_favorite where work_id = ?", String.class, workId);
    }

    private Integer rowCountOf(long workId) {
        return jdbcTemplate.queryForObject(
                "select count(*) from work_favorite where work_id = ?", Integer.class, workId);
    }
}
