package com.test.test.integration;

import com.test.test.integration.support.AdminApiTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 관리 API 인가 (02_API_명세서 §0-2·§0-3) — TDD Red.
 *
 * <ul>
 *   <li>비로그인 → 401 JSON {@code NOT_AUTHENTICATED} (기존 진입점)</li>
 *   <li>USER → 403 JSON {@code ACCESS_DENIED} (신규 AccessDeniedHandler — 현재는 빈 403)</li>
 *   <li>ADMIN → 200</li>
 * </ul>
 * {@code /api/admin/**} 전체가 {@code hasAuthority("ADMIN")} 한 줄로 막혀야 하므로 GET·POST 를 섞어 확인한다.
 */
class AdminAuthIntegrationTest extends AdminApiTestSupport {

    @Test
    void anonymous_gets_401_json_on_admin_api() throws Exception {
        mockMvc.perform(get("/api/admin/dashboard"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("NOT_AUTHENTICATED"));

        mockMvc.perform(post("/api/admin/composers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(composerBody("아무개", "Anon, Test"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("NOT_AUTHENTICATED"));
    }

    @Test
    void user_role_gets_403_json_access_denied() throws Exception {
        Tokens user = loginUser();

        mockMvc.perform(get("/api/admin/dashboard").header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"))
                .andExpect(jsonPath("$.message").isString());

        mockMvc.perform(get("/api/admin/crawl/jobs/active").header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));

        adminPost(user, "/api/admin/composers", composerBody("아무개", "Anon, Test " + uniq()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    }

    @Test
    void admin_role_passes() throws Exception {
        Tokens admin = loginAdmin();

        adminGet(admin, "/api/admin/dashboard")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalWorks").isNumber());

        adminGet(admin, "/api/admin/crawl/jobs/active")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void admin_is_identified_by_roles_in_users_me() throws Exception {
        Tokens admin = loginAdmin();
        mockMvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, bearer(admin.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roles").isArray())
                .andExpect(jsonPath("$.data.roles[?(@ == 'ADMIN')]").exists());

        Tokens user = loginUser();
        mockMvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roles[?(@ == 'ADMIN')]").doesNotExist());
    }

    /**
     * qa 결함 D9 — 위조(서명 불일치)·형식 오류 토큰에 {@code TOKEN_EXPIRED} 가 나온다.
     *
     * <p>계약(02 §0-2)의 세 값은 뜻이 다르다: {@code NOT_AUTHENTICATED}(토큰 없음) /
     * {@code TOKEN_EXPIRED}(유효한 서명인데 시간이 지남 → <b>프론트가 refresh 를 시도</b>) /
     * {@code INVALID_TOKEN}(못 믿을 토큰 → 재발급 시도 없이 로그인으로).
     * 지금은 위조 토큰에도 {@code TOKEN_EXPIRED} 가 나가 프론트({@code lib/http.js})가 무의미한 refresh 를 한다.
     */
    @Test
    void forged_or_malformed_token_returns_401_invalid_token() throws Exception {
        Tokens admin = loginAdmin();

        // 서명만 한 글자 바꾼 위조 토큰 (헤더·페이로드는 그대로)
        String valid = admin.accessToken();
        String forged = valid.substring(0, valid.length() - 1) + (valid.endsWith("A") ? "B" : "A");
        mockMvc.perform(get("/api/admin/dashboard").header(HttpHeaders.AUTHORIZATION, bearer(forged)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("INVALID_TOKEN"));

        // JWT 형식이 아예 아닌 값
        mockMvc.perform(get("/api/admin/dashboard").header(HttpHeaders.AUTHORIZATION, bearer("not-a-jwt")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_TOKEN"));

        // 토큰이 아예 없으면 지금처럼 NOT_AUTHENTICATED (회귀 가드)
        mockMvc.perform(get("/api/admin/dashboard"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("NOT_AUTHENTICATED"));
    }
}
