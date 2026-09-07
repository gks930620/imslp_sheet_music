package com.test.test.stomp.interceptor;

import com.test.test.jwt.JwtUtil;
import com.test.test.jwt.service.CustomUserDetailsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

/**
 * STOMP 채널 인터셉터 — CONNECT 시 JWT 인증
 * 1) STOMP Authorization 헤더 확인 (앱 클라이언트)
 * 2) 없으면 HttpHandshakeInterceptor가 쿠키에서 추출해둔 username 확인 (브라우저)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtChannelInterceptor implements ChannelInterceptor {

    private final JwtUtil jwtUtil;
    private final CustomUserDetailsService userDetailsService;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);

        if (!StompCommand.CONNECT.equals(accessor.getCommand())) {
            return message;  // CONNECT가 아니면 토큰검증 안함
        }

        String username = null;

        // 1. STOMP Authorization 헤더에서 JWT 추출 (앱 클라이언트용)
        String token = accessor.getFirstNativeHeader("Authorization");
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
            // access 토큰만 허용 — 장수명 refresh 토큰으로 CONNECT 인증되는 것 방지
            if ("access".equals(jwtUtil.getTokenType(token))) {
                username = jwtUtil.validateAndExtractUsername(token);
            } else {
                log.warn("WebSocket CONNECT 거부 - access 토큰이 아님");
            }
        }

        // 2. 헤더에 없으면 HttpHandshakeInterceptor가 쿠키에서 추출해둔 username 사용 (브라우저용)
        if (username == null && accessor.getSessionAttributes() != null) {
            Object cookieUsername = accessor.getSessionAttributes().get("cookieUsername");
            if (cookieUsername instanceof String) {
                username = (String) cookieUsername;
            }
        }

        if (username == null) {
            log.warn("WebSocket CONNECT 인증 실패 - 토큰 없음");
            return null; // 연결 거부
        }

        // 인증 정보 설정
        UserDetails userDetails = userDetailsService.loadUserByUsername(username);
        UsernamePasswordAuthenticationToken authenticationToken =
            new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());

        // 세션 속성은 SockJS 핸드셰이크가 있을 때만 존재(브라우저). 앱 클라이언트(raw WS)는 null일 수 있어 방어.
        var sessionAttributes = accessor.getSessionAttributes();
        if (sessionAttributes != null) {
            sessionAttributes.put("user", authenticationToken);
            sessionAttributes.put("roomId", accessor.getFirstNativeHeader("roomId"));
        }
        accessor.setUser(authenticationToken);

        log.debug("WebSocket CONNECT 인증 성공: {}", username);
        return message;
    }
}

