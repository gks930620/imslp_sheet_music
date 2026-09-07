package com.test.test.common.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 배포 헬스체크 엔드포인트 (컨벤션 §5-4).
 * Railway/Docker 가 인증 없이 호출한다 — DB 등 의존성 상태는 actuator 가 담당하고,
 * 여기는 "프로세스가 요청을 받을 수 있는가"만 답한다.
 */
@RestController
public class HealthController {

    @GetMapping("/healthz")
    public Map<String, String> healthz() {
        return Map.of("status", "UP");
    }
}
