package com.test.test.stomp.controller;

import com.test.test.stomp.model.ChatMessage;
import com.test.test.stomp.repository.RoomRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;

@Slf4j
@Controller
@RequiredArgsConstructor
public class ChatController {

    private final SimpMessagingTemplate messagingTemplate;
    private final RoomRepository roomRepository;

    // 클라이언트 -> 서버
    @MessageMapping("/room/{roomId}")   //pub일 때, send일 때만 옴
    public void sendMessage(
        @DestinationVariable String roomId,
        ChatMessage message,
        Message<?> msg
    ) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(msg);

        // 인증 주체 확인: CONNECT에서 setUser한 Principal을 우선 사용(브라우저/앱 공통),
        // 없으면 세션 속성(브라우저 SockJS)으로 폴백. 둘 다 없으면 미인증이므로 전송 무시.
        // (기존엔 getSessionAttributes()가 null인 앱 클라이언트에서 NPE 500 발생)
        String username = resolveUsername(accessor);
        if (username == null) {
            return;
        }

        // 존재하지 않는 방으로의 전송 차단. (참여자 멤버십 검증은 멤버십 도메인 부재로 미구현 — 설계상 한계)
        if (!roomExists(roomId)) {
            log.warn("존재하지 않는 방으로의 메시지 전송 무시 - roomId: {}", roomId);
            return;
        }

        message.setSender(username);
        messagingTemplate.convertAndSend("/sub/room/" + roomId, message);
    }

    private boolean roomExists(String roomId) {
        try {
            return roomRepository.existsById(Long.parseLong(roomId));
        } catch (NumberFormatException e) {
            return false; // 숫자가 아닌 roomId는 유효하지 않음
        }
    }

    private String resolveUsername(StompHeaderAccessor accessor) {
        if (accessor.getUser() != null) {
            return accessor.getUser().getName();
        }
        if (accessor.getSessionAttributes() != null
                && accessor.getSessionAttributes().get("user") instanceof Authentication auth) {
            return auth.getName();
        }
        return null;
    }
}

