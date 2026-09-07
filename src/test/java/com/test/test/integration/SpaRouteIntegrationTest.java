package com.test.test.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SPA 라우트 등록 방식 — docs/설계/02_API_명세서.md §0-5, 03_기술결정.md §10.
 *
 * <ul>
 *   <li>HomeController 는 catch-all: 점(.)이 없는 첫 세그먼트가 api/uploads/assets/… 가 아니면 {@code forward:/index.html}</li>
 *   <li>SecurityConfig: 화면 셸 GET(/search, /works/**, /composers/**, /admin, /admin/**)은 비로그인 permitAll</li>
 *   <li>정적 파일(점이 든 세그먼트)·/api·/uploads 는 forward 되지 않는다</li>
 * </ul>
 * 보안 필터를 그대로 태워(addFilters 기본값) permitAll 회귀까지 잡는다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SpaRouteIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @ParameterizedTest(name = "GET {0} → 200 forward:/index.html (비로그인)")
    @DisplayName("새 화면 셸 라우트는 비로그인으로 index.html 로 forward 된다")
    @ValueSource(strings = {
            "/", "/search", "/works/21", "/composers", "/composers/9",
            "/admin", "/admin/works", "/admin/works/21/edit", "/admin/crawl/12",
            // 기존 라우트 유지
            "/login", "/signup", "/mypage", "/community/list", "/rooms/3",
            // 등록되지 않은 임의 경로도 SPA 가 404 화면을 그린다(HomeController 경로 열거 제거)
            "/some/unknown/route"
    })
    void spaShellRoutes_forwardToIndex(String path) throws Exception {
        mockMvc.perform(get(path))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/index.html"));
    }

    @ParameterizedTest(name = "GET {0} 는 index.html 로 forward 되지 않는다")
    @DisplayName("API·정적 파일·업로드·인프라 경로는 catch-all 에서 제외")
    @ValueSource(strings = {
            "/api/works/search",            // API — 컨트롤러가 처리(400 등), forward 아님
            "/api/no-such-endpoint-zz",     // 미등록 API — anyRequest().authenticated() → 401, forward 아님
            "/assets/app.js",               // 점이 든 세그먼트 = 정적 파일
            "/favicon.ico",
            "/uploads/no-such-file.png",    // FileServingController → 404
            "/actuator/health",
            "/healthz",
            "/h2-console/login.jsp"
    })
    void nonSpaPaths_areNotForwarded(String path) throws Exception {
        mockMvc.perform(get(path))
                .andExpect(forwardedUrl(null));
    }

    @Test
    @DisplayName("점(.)이 든 세그먼트는 정적 파일로 보고 forward 하지 않는다 → 404")
    void dottedSegment_isStaticNotForwarded() throws Exception {
        mockMvc.perform(get("/works/score.pdf"))
                .andExpect(forwardedUrl(null))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("/api 아래 미등록 경로는 401 JSON (anyRequest().authenticated() 유지)")
    void unknownApi_requiresAuth() throws Exception {
        mockMvc.perform(get("/api/no-such-endpoint-zz"))
                .andExpect(status().isUnauthorized());
    }
}
