package com.test.test.jwt.controller;

import com.test.test.common.dto.ApiResponse;
import com.test.test.jwt.service.AppOAuth2Service;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/oauth2/providers")
@RequiredArgsConstructor
public class AppOAuth2Controller {

    private final AppOAuth2Service appOAuth2Service;

    @PostMapping("/{provider}/tokens")
    public ResponseEntity<ApiResponse<Map<String, String>>> oauthAppLogin(
            @PathVariable("provider") String provider,
            @RequestBody Map<String, String> request) {
        Map<String, String> tokenData = appOAuth2Service.login(provider, request);
        return ResponseEntity.ok(ApiResponse.success("OAuth login success", tokenData));
    }
}
