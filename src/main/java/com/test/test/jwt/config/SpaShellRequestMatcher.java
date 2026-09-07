package com.test.test.jwt.config;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.http.HttpMethod;
import org.springframework.security.web.util.matcher.RequestMatcher;

/**
 * SPA 화면 셸 GET 요청 매처 (02_API_명세서 §0-5).
 *
 * <p>{@code HomeController} 가 경로 열거를 버리고 catch-all 로 forward 하므로, 시큐리티도 같은 기준으로
 * "API·인프라가 아닌 GET" 을 공개한다. 데이터는 {@code /api/**} 가 막으므로 셸 HTML 공개는 안전하다
 * (예: {@code /admin} 은 열리지만 {@code /api/admin/**} 은 ADMIN 만).
 */
public class SpaShellRequestMatcher implements RequestMatcher {

    /** 셸이 아닌 경로 — API·업로드 프록시·인프라·OAuth2 엔드포인트. */
    private static final List<String> EXCLUDED_PREFIXES = List.of(
            "/api", "/actuator", "/error", "/h2-console", "/swagger-ui", "/v3",
            "/oauth2", "/custom-oauth2", "/login/oauth2", "/ws-chat", "/healthz");

    @Override
    public boolean matches(HttpServletRequest request) {
        if (!HttpMethod.GET.matches(request.getMethod())) {
            return false;
        }
        String path = request.getRequestURI();
        if (request.getContextPath() != null && !request.getContextPath().isEmpty()
                && path.startsWith(request.getContextPath())) {
            path = path.substring(request.getContextPath().length());
        }
        for (String prefix : EXCLUDED_PREFIXES) {
            if (path.equals(prefix) || path.startsWith(prefix + "/")) {
                return false;
            }
        }
        return true;
    }
}
