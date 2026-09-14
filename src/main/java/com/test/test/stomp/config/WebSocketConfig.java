package com.test.test.stomp.config;

import com.test.test.jwt.JwtUtil;
import com.test.test.stomp.interceptor.HttpHandshakeInterceptor;
import com.test.test.stomp.interceptor.JwtChannelInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.*;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtUtil jwtUtil;
    private final JwtChannelInterceptor jwtChannelInterceptor;

    // 핸드셰이크는 쿠키의 access_token으로 인증되므로, 임의 출처를 허용하면 CSWSH(교차출처 WebSocket 하이재킹)에
    // 노출된다. 와일드카드("*") 대신 신뢰 출처만 허용한다. 운영 도메인은 app.websocket.allowed-origins 로 주입.
    @Value("${app.websocket.allowed-origins:http://localhost:3000,http://localhost:5104,http://localhost:8104}")
    private String[] allowedOrigins;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws-chat")
            .setAllowedOriginPatterns(allowedOrigins)
            .addInterceptors(new HttpHandshakeInterceptor(jwtUtil))  // 쿠키에서 JWT 추출
            .withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.setApplicationDestinationPrefixes("/pub");  // 클라이언트 → 서버
        registry.enableSimpleBroker("/sub");                  // 서버 → 클라이언트 (구독)
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(jwtChannelInterceptor);
    }
}

