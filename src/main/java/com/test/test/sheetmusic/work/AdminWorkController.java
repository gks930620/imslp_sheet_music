package com.test.test.sheetmusic.work;

import com.test.test.common.dto.ApiResponse;
import com.test.test.sheetmusic.common.AdminPageRequests;
import com.test.test.sheetmusic.recommendation.dto.RecommendationHistoryDTO;
import com.test.test.sheetmusic.work.dto.AdminWorkDetailDTO;
import com.test.test.sheetmusic.work.dto.AdminWorkListDTO;
import com.test.test.sheetmusic.work.dto.AliasOverlapDTO;
import com.test.test.sheetmusic.work.dto.RecommendationReviewDTO;
import com.test.test.sheetmusic.work.dto.WorkSaveDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 곡 관리 API (02 §4-6 ~ §4-10). */
@RestController
@RequestMapping("/api/admin/works")
@RequiredArgsConstructor
public class AdminWorkController {

    private final AdminWorkService adminWorkService;

    @GetMapping
    public ResponseEntity<ApiResponse<AdminWorkListDTO>> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long composerId,
            @RequestParam(required = false) String level,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) Integer size) {
        return ResponseEntity.ok(ApiResponse.success(
                adminWorkService.list(q, status, composerId, level, AdminPageRequests.of(page, size))));
    }

    /** {@code /{id}} 보다 먼저 매칭되도록 구체 경로를 위에 둔다. */
    @GetMapping("/aliases/overlap")
    public ResponseEntity<ApiResponse<AliasOverlapDTO>> aliasOverlap(
            @RequestParam String alias,
            @RequestParam(required = false) Long excludeWorkId) {
        return ResponseEntity.ok(ApiResponse.success(adminWorkService.aliasOverlap(alias, excludeWorkId)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AdminWorkDetailDTO>> detail(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(adminWorkService.detail(id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<AdminWorkDetailDTO>> create(@Valid @RequestBody WorkSaveDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(adminWorkService.create(request)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<AdminWorkDetailDTO>> update(@PathVariable Long id,
                                                                  @Valid @RequestBody WorkSaveDTO request) {
        return ResponseEntity.ok(ApiResponse.success(adminWorkService.update(id, request)));
    }

    /** 바뀐 이력 전부 (02 §4-7-1) — 곡 상세는 최근 5줄만 싣는다. */
    @GetMapping("/{workId}/recommendation-history")
    public ResponseEntity<ApiResponse<RecommendationHistoryDTO>> recommendationHistory(@PathVariable Long workId) {
        return ResponseEntity.ok(ApiResponse.success(adminWorkService.recommendationHistory(workId)));
    }

    /**
     * 추천 판본 확인함/되돌리기 (02 §5-6-1) — 응답은 곡 상세(관리) 그대로다.
     * 추천 지정(§5-6)은 판본 API 에 있지만 이것이 바꾸는 것은 곡의 상태이고 응답도 곡이라 여기에 둔다.
     */
    @PutMapping("/{workId}/recommended-edition/review")
    public ResponseEntity<ApiResponse<AdminWorkDetailDTO>> reviewRecommendation(
            @PathVariable Long workId,
            @Valid @RequestBody RecommendationReviewDTO request) {
        return ResponseEntity.ok(ApiResponse.success(
                adminWorkService.reviewRecommendation(workId, request.getReviewed())));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        adminWorkService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
