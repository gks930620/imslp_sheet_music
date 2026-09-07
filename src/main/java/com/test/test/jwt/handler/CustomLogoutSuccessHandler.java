package com.test.test.jwt.handler;

import com.test.test.jwt.service.RefreshService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.stereotype.Component;

/**
 * 로그아웃 성공 시 처리하는 Handler
 * - config가 아닌 handler 패키지로 이동 (역할에 맞는 패키지 분리)
 */
@Component
@RequiredArgsConstructor
public class CustomLogoutSuccessHandler implements LogoutSuccessHandler {

    private final RefreshService refreshService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${app.cookie.secure:false}")
    private boolean secureCookie;  // 로컬(HTTP): false, 운영(HTTPS): true

    @Override
    public void onLogoutSuccess(HttpServletRequest request, HttpServletResponse response,
        Authentication authentication)
        throws IOException, ServletException {

        // 1~2. 서버 측 Refresh Token 무효화
        //  - 인증 주체가 확정된 경우(access 토큰으로 인증된 로그아웃): username 기준으로 전부 삭제하는 것이 가장 확실.
        //    (앱이 로그아웃 요청에 access 토큰을 보내면 refresh 값 완전일치 삭제가 매칭 실패하던 문제 해소)
        //  - 미인증(예: access 만료)인 경우에만 쿠키/헤더의 refresh 토큰 값으로 폴백 삭제.
        if (authentication != null && authentication.getName() != null) {
            refreshService.deleteRefreshByUsername(authentication.getName());
        } else {
            String refreshToken = null;
            if (request.getCookies() != null) {
                refreshToken = Arrays.stream(request.getCookies())
                    .filter(cookie -> "refresh_token".equals(cookie.getName()))
                    .map(Cookie::getValue)
                    .findFirst()
                    .orElse(null);
            }
            if (refreshToken == null) {
                String authHeader = request.getHeader("Authorization");
                if (authHeader != null && authHeader.startsWith("Bearer ")) {
                    refreshToken = authHeader.substring(7);
                }
            }
            if (refreshToken != null) {
                refreshService.deleteRefresh(refreshToken);
            }
        }

        // 3. SecurityContext 클리어
        SecurityContextHolder.clearContext();

        // 4. 쿠키 만료 (브라우저용 - 앱은 쿠키를 사용하지 않으므로 무시됨)
        expireCookie(response, "access_token");
        expireCookie(response, "refresh_token");

        // 5. JSON 응답 (웹/앱 모두)
        // - 브라우저: 응답 받고 리다이렉트 처리 (쿠키는 이미 서버에서 만료시킴)
        // - 앱: 응답 받고 앱 자체에서 토큰 삭제 후 로그인 화면으로 이동
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setStatus(HttpServletResponse.SC_OK);
        response.getWriter().write(objectMapper.writeValueAsString(
            Map.of("message", "Logged out successfully")));
    }

    private void expireCookie(HttpServletResponse response, String name) {
        ResponseCookie cookie = ResponseCookie.from(name, "")
            .path("/")
            .maxAge(0)
            .httpOnly(true)
            .secure(secureCookie)  // 로컬(HTTP): false, 운영(HTTPS): true
            .sameSite("Lax")
            .build();
        response.addHeader("Set-Cookie", cookie.toString());
    }
}

