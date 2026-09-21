package com.test.test.sheetmusic.member;

import com.test.test.common.dto.ApiResponse;
import com.test.test.jwt.model.CustomUserAccount;
import com.test.test.sheetmusic.common.CurrentUser;
import com.test.test.sheetmusic.member.dto.DownloadLibraryDTO;
import com.test.test.sheetmusic.member.dto.FavoriteLibraryDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 내 악보 (02 §10-2 · §10-3). 탭마다 자기 주소다 — 삭제·비우기 문은 만들지 않는다(§10-0, 기획 05 §3-4).
 * 사용자 id 를 받는 자리가 없어서 "남의 것 보기" 는 시도할 주소가 없다(§10-0).
 */
@RestController
@RequestMapping("/api/me/library")
@RequiredArgsConstructor
public class MyLibraryController {

    private final MyLibraryQueryService myLibraryQueryService;

    @GetMapping("/favorites")
    public ResponseEntity<ApiResponse<FavoriteLibraryDTO>> favorites(
            @RequestParam(required = false) String section,
            @PageableDefault(size = 20) Pageable pageable,
            @AuthenticationPrincipal CustomUserAccount account) {
        return ResponseEntity.ok(ApiResponse.success(
                myLibraryQueryService.favorites(CurrentUser.id(account), section, pageable)));
    }

    @GetMapping("/downloads")
    public ResponseEntity<ApiResponse<DownloadLibraryDTO>> downloads(
            @RequestParam(required = false) String section,
            @PageableDefault(size = 20) Pageable pageable,
            @AuthenticationPrincipal CustomUserAccount account) {
        return ResponseEntity.ok(ApiResponse.success(
                myLibraryQueryService.downloads(CurrentUser.id(account), section, pageable)));
    }
}
