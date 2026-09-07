package com.test.test.sheetmusic.work;

import com.test.test.common.dto.ApiResponse;
import com.test.test.sheetmusic.common.AdminPageRequests;
import com.test.test.sheetmusic.work.dto.AdminWorkDetailDTO;
import com.test.test.sheetmusic.work.dto.AdminWorkListDTO;
import com.test.test.sheetmusic.work.dto.AliasOverlapDTO;
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

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        adminWorkService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
