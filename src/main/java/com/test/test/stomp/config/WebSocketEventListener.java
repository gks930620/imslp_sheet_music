package com.test.test.stomp.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

@Component
@RequiredArgsConstructor
public class WebSocketEventListener {

    private final SimpMessagingTemplate messagingTemplate;

    // 연결됐을 때 한번 실행
    // 순서: JwtChannelInterceptor → 실제 연결 → 여기
    @EventListener
    public void handleWebSocketConnectListener(SessionConnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());

        // 세션 속성은 SockJS 핸드셰이크(브라우저)에서만 존재. 앱(raw WS) 클라이언트는 null → NPE 방어.
        var sessionAttributes = accessor.getSessionAttributes();
        if (sessionAttributes == null) {
            return;
        }

        Authentication auth = (Authentication) sessionAttributes.get("user");
        String roomId = (String) sessionAttributes.get("roomId");

        if (auth != null && roomId != null) {
            String username = auth.getName();
            messagingTemplate.convertAndSend("/sub/room/" + roomId, username + "님이 입장했습니다.");
        }
    }

    // 연결 해제(퇴장) — 뒤로가기, 브라우저 종료 등 모두 감지
    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());

        var sessionAttributes = accessor.getSessionAttributes();
        if (sessionAttributes == null) {
            return;
        }

        Authentication auth = (Authentication) sessionAttributes.get("user");
        String roomId = (String) sessionAttributes.get("roomId");

        if (auth != null && roomId != null) {
            String username = auth.getName();
            messagingTemplate.convertAndSend("/sub/room/" + roomId, username + "님이 퇴장했습니다.");
        }
    }
}

