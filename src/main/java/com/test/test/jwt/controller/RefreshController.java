package com.test.test.jwt.controller;

import com.test.test.common.dto.ApiResponse;
import com.test.test.common.exception.RefreshTokenException;
import com.test.test.jwt.service.RefreshService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Arrays;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tokens")
@RequiredArgsConstructor
public class RefreshController {

    private final RefreshService refreshService;

    @Value("${app.cookie.secure:false}")
    private boolean secureCookie;

    @PostMapping("/refresh")
    public ResponseEntity<?> refreshAccessToken(
            HttpServletRequest request,
            HttpServletResponse response,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        String token = extractRefreshToken(request, authHeader);
        if (token == null) {
            throw new RefreshTokenException("Refresh token is required", "TOKEN_REQUIRED");
        }

        // 회전(검증→삭제→신규 발급)은 서비스에서 원자적으로 처리
        Map<String, String> tokens = refreshService.rotate(token);

        boolean isWebRequest = (authHeader == null || !authHeader.startsWith("Bearer "));
        if (isWebRequest) {
            addCookie(response, "access_token", tokens.get("access_token"));
            addCookie(response, "refresh_token", tokens.get("refresh_token"));
            return ResponseEntity.ok(ApiResponse.success("Token refreshed", null));
        }

        return ResponseEntity.ok(ApiResponse.success("Token refreshed", tokens));
    }

    private String extractRefreshToken(HttpServletRequest request, String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.replace("Bearer ", "");
        }

        if (request.getCookies() != null) {
            return Arrays.stream(request.getCookies())
                    .filter(cookie -> "refresh_token".equals(cookie.getName()))
                    .map(Cookie::getValue)
                    .findFirst()
                    .orElse(null);
        }

        return null;
    }

    private void addCookie(HttpServletResponse response, String name, String value) {
        ResponseCookie cookie = ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(secureCookie)
                .sameSite("Lax")
                .path("/")
                .build();
        response.addHeader("Set-Cookie", cookie.toString());
    }
}
