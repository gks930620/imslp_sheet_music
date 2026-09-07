package com.test.test.jwt.repository;

import jakarta.annotation.PreDestroy;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.stereotype.Component;

/**
 * OAuth2 인증 요청을 메모리에 임시 저장하는 Repository
 * - config가 아닌 repository 패키지로 이동 (역할에 맞는 패키지 분리)
 * - TODO: 서버 다중화 시 Redis로 변경 필요
 */
@Component
public class InMemoryAuthorizationRequestRepository implements
    AuthorizationRequestRepository<OAuth2AuthorizationRequest> {

    // 지금은 Map이지만 서버 다중화 시 Redis로 변경 필요
    // 내 서버1 -> 카카오 -> 내 서버2로 올 때 Redis를 통해 state를 조회
    private final Map<String, OAuth2AuthorizationRequest> authorizationRequests = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    @Override
    public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
        String state = request.getParameter("state");
        if (state == null) {
            return null;
        }
        return authorizationRequests.get(state);
    }

    @Override
    public void saveAuthorizationRequest(OAuth2AuthorizationRequest authorizationRequest, HttpServletRequest request, HttpServletResponse response) {
        if (authorizationRequest == null) {
            return;
        }

        String state = authorizationRequest.getState();
        authorizationRequests.put(state, authorizationRequest);

        // 5분 후 자동 삭제 (보안 상 Authorization Request를 계속 들고 있을 필요 없음)
        scheduler.schedule(() -> authorizationRequests.remove(state), 5, TimeUnit.MINUTES);
    }

    // OAuth2 로그인 성공 후 리다이렉트 되었을 때, Spring Security 필터에 의해 호출됨
    @Override
    public OAuth2AuthorizationRequest removeAuthorizationRequest(HttpServletRequest request, HttpServletResponse response) {
        String state = request.getParameter("state");
        if (state == null) {
            return null;
        }
        // 실제 삭제는 SuccessHandler가 deleteAuthorizationRequest로 명시적으로 수행한다.
        // (state 값을 stdout으로 노출하지 않는다 — 운영 로그 오염/민감정보 노출 방지)
        return authorizationRequests.get(state);
    }

    // 로그인 성공 후 명시적으로 삭제하기 위한 메서드 (SuccessHandler에서 호출)
    public void deleteAuthorizationRequest(String state) {
        if (state != null) {
            authorizationRequests.remove(state);
        }
    }

    // 스케줄러 스레드 누수 방지: 컨텍스트 종료/재배포 시 정리
    @PreDestroy
    public void shutdown() {
        scheduler.shutdownNow();
    }
}

