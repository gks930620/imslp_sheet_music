package com.test.test.jwt.filter;

import com.test.test.jwt.JwtUtil;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

@Slf4j
@RequiredArgsConstructor
public class JwtAccessTokenCheckAndSaveUserInfoFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final UserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String token = getTokenFromRequest(request);
        if (token == null) {
            chain.doFilter(request, response);
            return;
        }

        if ("refresh".equals(jwtUtil.getTokenType(token))) {
            // Refresh token can pass through to /api/tokens/refresh.
            chain.doFilter(request, response);
            return;
        }

        String username = resolveUsername(token, request);
        if (username == null) {
            // 위조·만료 사유는 ERROR_CAUSE 에 담겼다. 인증 없이 통과시키면 진입점이 401 로 마무리한다.
            chain.doFilter(request, response);
            return;
        }

        try {
            UserDetails userDetails = userDetailsService.loadUserByUsername(username);
            UsernamePasswordAuthenticationToken authenticationToken =
                    new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
            authenticationToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authenticationToken);
        } catch (UsernameNotFoundException e) {
            log.warn("User from JWT token not found: {}", e.getMessage());
            clearTokenCookies(response);
        }

        chain.doFilter(request, response);
    }

    /**
     * 토큰에서 사용자명을 꺼낸다. 꺼내지 못하면 {@code null} 을 주고 사유를 {@code ERROR_CAUSE} 에 남긴다.
     *
     * <p>만료와 위조를 구분하는 지점이다 (02 §0-2). {@code parseSignedClaims} 는 <b>서명을 먼저 검증</b>하고,
     * 서명이 맞을 때만 만료로 {@link ExpiredJwtException} 을 던진다. 그래서
     * 서명 불일치·JWT 형식 아님은 {@code INVALID_TOKEN}, 유효기간 지남은 {@code TOKEN_EXPIRED} 로 갈린다.
     * (예전처럼 둘을 {@code validateToken} 의 false 하나로 뭉개면 프론트가 위조 토큰으로 재발급을 시도한다.)
     */
    private String resolveUsername(String token, HttpServletRequest request) {
        try {
            return jwtUtil.extractUsername(token);
        } catch (ExpiredJwtException e) {
            request.setAttribute("ERROR_CAUSE", "TOKEN_EXPIRED");
            return null;
        } catch (JwtException | IllegalArgumentException e) {
            request.setAttribute("ERROR_CAUSE", "INVALID_TOKEN");
            return null;
        }
    }

    private void clearTokenCookies(HttpServletResponse response) {
        Cookie accessTokenCookie = new Cookie("access_token", null);
        accessTokenCookie.setMaxAge(0);
        accessTokenCookie.setPath("/");
        response.addCookie(accessTokenCookie);

        Cookie refreshTokenCookie = new Cookie("refresh_token", null);
        refreshTokenCookie.setMaxAge(0);
        refreshTokenCookie.setPath("/");
        response.addCookie(refreshTokenCookie);
    }

    private String getTokenFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }

        if (request.getCookies() != null) {
            return Arrays.stream(request.getCookies())
                    .filter(cookie -> "access_token".equals(cookie.getName()))
                    .map(Cookie::getValue)
                    .findFirst()
                    .orElse(null);
        }
        return null;
    }
}
