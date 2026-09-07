package com.test.test.sheetmusic.composer;

import com.test.test.common.dto.ApiResponse;
import com.test.test.sheetmusic.composer.dto.ComposerCardDTO;
import com.test.test.sheetmusic.composer.dto.ComposerDetailDTO;
import com.test.test.sheetmusic.composer.dto.ComposerListDTO;
import com.test.test.sheetmusic.work.WorkQueryService;
import com.test.test.sheetmusic.work.dto.ComposerWorksResponseDTO;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 공개 작곡가 API (02 §3-5 ~ §3-8). */
@RestController
@RequestMapping("/api/composers")
@RequiredArgsConstructor
public class ComposerController {

    private final ComposerQueryService composerQueryService;
    private final WorkQueryService workQueryService;

    @GetMapping
    public ResponseEntity<ApiResponse<ComposerListDTO>> list() {
        return ResponseEntity.ok(ApiResponse.success(composerQueryService.list()));
    }

    @GetMapping("/featured")
    public ResponseEntity<ApiResponse<List<ComposerCardDTO>>> featured(
            @RequestParam(defaultValue = "8") int limit) {
        return ResponseEntity.ok(ApiResponse.success(composerQueryService.featured(limit)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ComposerDetailDTO>> detail(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(composerQueryService.detail(id)));
    }

    @GetMapping("/{id}/works")
    public ResponseEntity<ApiResponse<ComposerWorksResponseDTO>> works(
            @PathVariable Long id,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String level,
            @RequestParam(required = false) String pages,
            @RequestParam(required = false) Boolean downloadable,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(
                workQueryService.composerWorks(id, sort, level, pages, downloadable, pageable)));
    }
}
