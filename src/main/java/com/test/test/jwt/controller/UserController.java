package com.test.test.jwt.controller;

import com.test.test.common.dto.ApiResponse;
import com.test.test.jwt.model.CustomUserAccount;
import com.test.test.jwt.model.UserDTO;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getMyInfo(
            @AuthenticationPrincipal CustomUserAccount customUserAccount) {
        if (customUserAccount == null) {
            return ResponseEntity.status(401).body(null);
        }

        UserDTO userDTO = customUserAccount.getUserDTO();

        Map<String, Object> userInfo = new HashMap<>();
        userInfo.put("id", userDTO.getId());
        userInfo.put("email", userDTO.getEmail());
        userInfo.put("username", userDTO.getUsername());
        userInfo.put("nickname", userDTO.getNickname());
        userInfo.put("provider", userDTO.getProvider());
        userInfo.put("roles", userDTO.getRoles());

        return ResponseEntity.ok(ApiResponse.success("My profile fetched", userInfo));
    }
}
