package com.test.test.sheetmusic.admin;

import com.test.test.common.dto.ApiResponse;
import com.test.test.sheetmusic.admin.dto.DashboardDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 관리 홈 API (02 §4-1). */
@RestController
@RequestMapping("/api/admin/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping
    public ResponseEntity<ApiResponse<DashboardDTO>> summary() {
        return ResponseEntity.ok(ApiResponse.success(dashboardService.summary()));
    }
}
