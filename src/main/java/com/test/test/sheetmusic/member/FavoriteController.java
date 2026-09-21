package com.test.test.sheetmusic.member;

import com.test.test.common.dto.ApiResponse;
import com.test.test.jwt.model.CustomUserAccount;
import com.test.test.sheetmusic.common.CurrentUser;
import com.test.test.sheetmusic.member.dto.FavoriteDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 즐겨찾기 (02 §10-1). 요청 본문은 없다 — 토글이 아니라 상태를 지정하는 두 문이다.
 *
 * <p>비로그인 401 은 {@code SecurityConfig} 의 {@code anyRequest().authenticated()} 가 준다 —
 * <b>{@code /api/me/**} 를 permitAll 목록에 넣지 않는 것이 계약이다</b>(02 §10-0).
 */
@RestController
@RequestMapping("/api/me/favorites")
@RequiredArgsConstructor
public class FavoriteController {

    private final FavoriteService favoriteService;

    @PutMapping("/{workId}")
    public ResponseEntity<ApiResponse<FavoriteDTO>> turnOn(@PathVariable Long workId,
                                                           @AuthenticationPrincipal CustomUserAccount account) {
        return ResponseEntity.ok(ApiResponse.success(favoriteService.turnOn(CurrentUser.id(account), workId)));
    }

    @DeleteMapping("/{workId}")
    public ResponseEntity<Void> turnOff(@PathVariable Long workId,
                                        @AuthenticationPrincipal CustomUserAccount account) {
        favoriteService.turnOff(CurrentUser.id(account), workId);
        return ResponseEntity.noContent().build();
    }
}
