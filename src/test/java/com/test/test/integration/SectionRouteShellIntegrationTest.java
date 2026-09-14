package com.test.test.integration;

import org.junit.jupiter.api.DisplayName;
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
 * 악기 구분 경로의 화면 셸 — docs/설계/02_API_명세서.md §0-5, 03_기술결정.md §21.
 *
 * <p><b>회귀 가드다(처음부터 초록이어야 한다).</b> "경로 접두사를 골라도 백엔드가 한 줄도 안 바뀐다" 가
 * 그 선택의 근거 4번이었고(03 §21-2), 그 근거가 사실인지 여기서 확인한다.
 * 누군가 {@code HomeController} 의 제외 목록이나 {@code SpaShellRequestMatcher} 를 건드려
 * {@code /piano/**} 가 401·404 가 되면 이 테스트가 먼저 깨진다.
 *
 * <p>보안 필터를 그대로 태워(addFilters 기본값) 비로그인 permitAll 회귀까지 본다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SectionRouteShellIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @ParameterizedTest(name = "GET {0} → 200 forward:/index.html (비로그인)")
    @DisplayName("구분 경로·준비 중 구분·없는 구분 이름 모두 SPA 셸로 간다 — 화면 라우팅은 프론트가 한다")
    @ValueSource(strings = {
            // 열린 구분
            "/piano", "/piano/search", "/piano/works/21", "/piano/composers", "/piano/composers/9",
            // 준비 중 구분 (안내 화면)
            "/violin", "/orchestra",
            // 준비 중 구분의 하위 경로 · 없는 구분 이름 — 화면은 404 지만 셸은 200 이다(SPA 가 그린다)
            "/violin/search", "/cello", "/cello/works/3",
            // 옛 주소는 계속 열린다 — 프론트가 /piano/… 로 replace 리다이렉트한다 (기획 04 §3-5)
            "/", "/search", "/works/21", "/composers", "/composers/9",
            // 관리 경로는 구분 밖 그대로
            "/admin", "/admin/works"
    })
    void sectionShellRoutes_forwardToIndex(String path) throws Exception {
        mockMvc.perform(get(path))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/index.html"));
    }
}
