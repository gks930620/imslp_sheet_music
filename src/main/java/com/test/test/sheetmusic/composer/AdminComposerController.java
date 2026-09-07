package com.test.test.sheetmusic.composer;

import com.test.test.common.dto.ApiResponse;
import com.test.test.common.dto.PageResponse;
import com.test.test.sheetmusic.common.AdminPageRequests;
import com.test.test.sheetmusic.composer.dto.AdminComposerDTO;
import com.test.test.sheetmusic.composer.dto.AdminComposerDetailDTO;
import com.test.test.sheetmusic.composer.dto.ComposerSaveDTO;
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

/** 작곡가 관리 API (02 §4-2 ~ §4-5). 인가는 SecurityConfig 의 {@code /api/admin/**} 한 줄. */
@RestController
@RequestMapping("/api/admin/composers")
@RequiredArgsConstructor
public class AdminComposerController {

    private final AdminComposerService adminComposerService;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<AdminComposerDTO>>> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false, defaultValue = "false") boolean missingKo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) Integer size) {
        return ResponseEntity.ok(ApiResponse.success(
                adminComposerService.list(q, missingKo, AdminPageRequests.of(page, size))));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AdminComposerDetailDTO>> detail(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(adminComposerService.detail(id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<AdminComposerDetailDTO>> create(@Valid @RequestBody ComposerSaveDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(adminComposerService.create(request)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<AdminComposerDetailDTO>> update(@PathVariable Long id,
                                                                      @Valid @RequestBody ComposerSaveDTO request) {
        return ResponseEntity.ok(ApiResponse.success(adminComposerService.update(id, request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        adminComposerService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
