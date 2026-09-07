package com.test.test.sheetmusic.crawl;

import com.test.test.common.dto.ApiResponse;
import com.test.test.common.dto.PageResponse;
import com.test.test.jwt.model.CustomUserAccount;
import com.test.test.sheetmusic.crawl.dto.CrawlCheckDTOs;
import com.test.test.sheetmusic.crawl.dto.CrawlJobDTO;
import com.test.test.sheetmusic.crawl.dto.CrawlJobDetailDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 수집 관리 API (02 §6). */
@RestController
@RequestMapping("/api/admin/crawl")
@RequiredArgsConstructor
public class CrawlController {

    private static final int PAGE_SIZE = 20;

    private final CrawlQueryService crawlQueryService;
    private final CrawlService crawlService;

    @PostMapping("/check")
    public ResponseEntity<ApiResponse<CrawlCheckDTOs.CheckResult>> check(
            @Valid @RequestBody CrawlCheckDTOs.CheckRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                crawlService.check(request.getUrls(), request.fetchFilesOrDefault())));
    }

    @PostMapping("/jobs")
    public ResponseEntity<ApiResponse<CrawlJobDTO>> createJob(
            @Valid @RequestBody CrawlCheckDTOs.JobRequest request,
            @AuthenticationPrincipal CustomUserAccount account) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                crawlService.createJob(request.getItems(), request.fetchFilesOrDefault(), username(account))));
    }

    @PostMapping("/jobs/{id}/stop")
    public ResponseEntity<ApiResponse<CrawlJobDTO>> stop(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(crawlService.stop(id)));
    }

    @PostMapping("/jobs/{id}/resume")
    public ResponseEntity<ApiResponse<CrawlJobDTO>> resume(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(crawlService.resume(id)));
    }

    @PostMapping("/jobs/{id}/retry-failed")
    public ResponseEntity<ApiResponse<CrawlJobDTO>> retryFailed(@PathVariable Long id,
                                                                @AuthenticationPrincipal CustomUserAccount account) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(crawlService.retryFailed(id, username(account))));
    }

    private String username(CustomUserAccount account) {
        return account == null ? null : account.getUsername();
    }

    @GetMapping("/jobs")
    public ResponseEntity<ApiResponse<PageResponse<CrawlJobDTO>>> list(@RequestParam(defaultValue = "0") int page) {
        return ResponseEntity.ok(ApiResponse.success(
                crawlQueryService.list(PageRequest.of(Math.max(page, 0), PAGE_SIZE))));
    }

    @GetMapping("/jobs/active")
    public ResponseEntity<ApiResponse<CrawlJobDTO>> active() {
        return ResponseEntity.ok(ApiResponse.success(crawlQueryService.active()));
    }

    @GetMapping("/jobs/{id}")
    public ResponseEntity<ApiResponse<CrawlJobDetailDTO>> detail(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(crawlQueryService.detail(id)));
    }
}
