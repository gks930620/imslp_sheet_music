package com.test.test.sheetmusic.work;

import com.test.test.common.dto.ApiResponse;
import com.test.test.jwt.model.CustomUserAccount;
import com.test.test.sheetmusic.common.CurrentUser;
import com.test.test.sheetmusic.work.dto.WorkDetailDTO;
import com.test.test.sheetmusic.work.dto.WorkSearchResponseDTO;
import com.test.test.sheetmusic.work.dto.WorkSummaryDTO;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 공개 곡 API (02 §3-1 ~ §3-3). */
@RestController
@RequestMapping("/api/works")
@RequiredArgsConstructor
public class WorkController {

    private final WorkQueryService workQueryService;

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<WorkSearchResponseDTO>> search(
            @RequestParam String q,
            @RequestParam(name = "in", required = false) String searchIn,
            @RequestParam(required = false) String section,
            @RequestParam(required = false) String level,
            @RequestParam(required = false) String pages,
            @RequestParam(required = false) Boolean downloadable,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(
                workQueryService.search(q, searchIn, section, level, pages, downloadable, pageable)));
    }

    @GetMapping("/popular")
    public ResponseEntity<ApiResponse<List<WorkSummaryDTO>>> popular(
            @RequestParam(required = false) String section,
            @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(ApiResponse.success(workQueryService.popular(section, limit)));
    }

    /**
     * 최근 본 곡의 "지금 정보" (02 §3-9) — 브라우저가 든 곡 id 목록을 받아 지금의 요약을 돌려준다.
     *
     * <p>{@code /{id}} 보다 <b>먼저 잡히는 리터럴 경로</b>다(선언 순서가 아니라 패턴 구체성이 정한다).
     * {@code ids} 가 아예 없으면 400 {@code MISSING_PARAMETER} — 화면은 저장된 곡이 0개면 요청을 보내지 않는다.
     */
    @GetMapping("/recent")
    public ResponseEntity<ApiResponse<List<WorkSummaryDTO>>> recent(
            @RequestParam String ids,
            @RequestParam(required = false) String section) {
        return ResponseEntity.ok(ApiResponse.success(workQueryService.recent(ids, section)));
    }

    /** 곡 상세는 {@code section} 을 받지 않는다 — 곡이 스스로 구분을 알고 응답에 싣는다(02 §0-7). */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<WorkDetailDTO>> detail(@PathVariable Long id,
                                                             @AuthenticationPrincipal CustomUserAccount account) {
        return ResponseEntity.ok(ApiResponse.success(
                workQueryService.detail(id, CurrentUser.idOrNull(account))));
    }
}
