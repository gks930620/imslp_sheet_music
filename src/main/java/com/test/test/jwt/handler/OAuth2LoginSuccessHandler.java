package com.test.test.jwt.handler;

import com.test.test.jwt.JwtUtil;
import com.test.test.jwt.repository.InMemoryAuthorizationRequestRepository;
import com.test.test.jwt.model.CustomUserAccount;
import com.test.test.jwt.service.RefreshService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

/**
 * OAuth2 로그인 성공 시 처리하는 Handler
 * - config가 아닌 handler 패키지로 이동 (역할에 맞는 패키지 분리)
 */
@Component
@RequiredArgsConstructor
public class OAuth2LoginSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final JwtUtil jwtUtil;
    private final RefreshService refreshService;
    private final InMemoryAuthorizationRequestRepository authorizationRequestRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${app.cookie.secure:false}")
    private boolean secureCookie;  // 로컬: false, 운영(HTTPS): true

    // OAuth2 인증 필터(OAuth2LoginAuthenticationFilter)가 인증을 완료한 후 호출됨
    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
        Authentication authentication) throws IOException, ServletException {

        CustomUserAccount customUserAccount = (CustomUserAccount) authentication.getPrincipal();
        String username = customUserAccount.getUsername();

        // 1. 토큰 생성
        String accessToken = jwtUtil.createAccessToken(username);
        String refreshToken = jwtUtil.createRefreshToken(username);
        // 2. Refresh 토큰 저장 (DB)
        refreshService.saveRefresh(refreshToken);

        // 3. 브라우저 OAuth2 로그인 성공 → HttpOnly 쿠키 설정 후 홈으로 리다이렉트.
        //    (앱은 이 브라우저 리다이렉트 플로우를 쓰지 않고 네이티브 SDK + AppOAuth2Controller 로 JWT를 발급받는다.
        //     과거의 "app" 분기는 토큰을 URL 쿼리스트링/하드코딩 평문 HTTP로 노출하는 도달 불가 죽은 코드였어 제거함)
        addCookie(response, "access_token", accessToken, -1);
        addCookie(response, "refresh_token", refreshToken, -1);

        getRedirectStrategy().sendRedirect(request, response, "/");

        // 5. 사용 완료된 요청 정보 명시적 삭제 (메모리 절약)
        String state = request.getParameter("state");
        if (state != null) {
            authorizationRequestRepository.deleteAuthorizationRequest(state);
        }
    }

    private void addCookie(HttpServletResponse response, String name, String value, int maxAge) {
        ResponseCookie.ResponseCookieBuilder cookieBuilder = ResponseCookie.from(name, value)
            .httpOnly(true)
            .secure(secureCookie)  // 로컬(HTTP): false, 운영(HTTPS): true
            .sameSite("Lax")
            .path("/");

        if (maxAge > 0) {
            cookieBuilder.maxAge(maxAge);
        }

        response.addHeader("Set-Cookie", cookieBuilder.build().toString());
    }
}

