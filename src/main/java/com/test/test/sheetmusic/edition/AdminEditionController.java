package com.test.test.sheetmusic.edition;

import com.test.test.common.dto.ApiResponse;
import com.test.test.jwt.model.CustomUserAccount;
import com.test.test.sheetmusic.common.AdminPageRequests;
import com.test.test.sheetmusic.edition.dto.AdminEditionDTO;
import com.test.test.sheetmusic.edition.dto.AutoJudgeDTOs;
import com.test.test.sheetmusic.edition.dto.CopyrightDTOs;
import com.test.test.sheetmusic.edition.dto.EditionFileUploadDTO;
import com.test.test.sheetmusic.edition.dto.EditionSaveDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** 판본·저작권 관리 API (02 §5). */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminEditionController {

    private final AdminEditionService adminEditionService;
    private final EditionFileService editionFileService;
    private final CopyrightAutoJudgeService copyrightAutoJudgeService;

    // ===== §5-1 업로드 =====

    @PostMapping("/edition-files")
    public ResponseEntity<ApiResponse<EditionFileUploadDTO>> upload(
            @RequestPart("file") MultipartFile file,
            @AuthenticationPrincipal CustomUserAccount account) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(editionFileService.upload(file, username(account))));
    }

    // ===== §5-2 / §5-3 / §5-4 / §5-5 =====

    @PostMapping("/works/{workId}/editions")
    public ResponseEntity<ApiResponse<AdminEditionDTO>> create(
            @PathVariable Long workId,
            @Valid @RequestBody EditionSaveDTO request,
            @AuthenticationPrincipal CustomUserAccount account) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(adminEditionService.create(workId, request, username(account))));
    }

    @PutMapping("/editions/{id}")
    public ResponseEntity<ApiResponse<AdminEditionDTO>> update(
            @PathVariable Long id,
            @Valid @RequestBody EditionSaveDTO request,
            @AuthenticationPrincipal CustomUserAccount account) {
        return ResponseEntity.ok(ApiResponse.success(adminEditionService.update(id, request, username(account))));
    }

    @GetMapping("/editions/{id}")
    public ResponseEntity<ApiResponse<AdminEditionDTO>> get(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(adminEditionService.get(id)));
    }

    @DeleteMapping("/editions/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        adminEditionService.delete(id);
        return ResponseEntity.noContent().build();
    }

    // ===== §5-6 추천 지정 =====

    @PutMapping("/works/{workId}/recommended-edition")
    public ResponseEntity<ApiResponse<CopyrightDTOs.RecommendResult>> recommend(
            @PathVariable Long workId,
            @Valid @RequestBody CopyrightDTOs.RecommendRequest request) {
        return ResponseEntity.ok(ApiResponse.success(adminEditionService.recommend(workId, request.getEditionId())));
    }

    // ===== §5-7 파일 받아오기 =====

    @PostMapping("/editions/{id}/fetch-file")
    public ResponseEntity<ApiResponse<CopyrightDTOs.FetchFileResult>> fetchFile(@PathVariable Long id) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.success(adminEditionService.requestFetch(id)));
    }

    // ===== §5-8 ~ §5-10 저작권 =====

    @GetMapping("/copyright/pending")
    public ResponseEntity<ApiResponse<CopyrightDTOs.PendingListResult>> pending(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Long composerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) Integer size) {
        return ResponseEntity.ok(ApiResponse.success(
                adminEditionService.pending(q, composerId, AdminPageRequests.of(page, size))));
    }

    @PutMapping("/editions/{id}/copyright")
    public ResponseEntity<ApiResponse<AdminEditionDTO>> judge(
            @PathVariable Long id,
            @Valid @RequestBody CopyrightDTOs.JudgeRequest request,
            @AuthenticationPrincipal CustomUserAccount account) {
        return ResponseEntity.ok(ApiResponse.success(adminEditionService.judge(id, request, username(account))));
    }

    // ===== §5-11 / §5-12 자동 판정 =====

    /** 본문은 생략 가능하다 — 없으면 {@code dryRun=false, assignRecommended=true} 기본값으로 돈다. */
    @PostMapping("/copyright/auto-judge")
    public ResponseEntity<ApiResponse<AutoJudgeDTOs.AutoJudgeResult>> autoJudge(
            @RequestBody(required = false) AutoJudgeDTOs.AutoJudgeRequest request) {
        AutoJudgeDTOs.AutoJudgeRequest resolved =
                request == null ? new AutoJudgeDTOs.AutoJudgeRequest() : request;
        return ResponseEntity.ok(ApiResponse.success(copyrightAutoJudgeService.autoJudge(resolved)));
    }

    @PostMapping("/copyright/auto-judge/undo")
    public ResponseEntity<ApiResponse<AutoJudgeDTOs.UndoResult>> undoAutoJudge() {
        return ResponseEntity.ok(ApiResponse.success(copyrightAutoJudgeService.undo()));
    }

    @PostMapping("/editions/copyright/bulk")
    public ResponseEntity<ApiResponse<CopyrightDTOs.BulkJudgeResult>> bulkJudge(
            @Valid @RequestBody CopyrightDTOs.BulkJudgeRequest request,
            @AuthenticationPrincipal CustomUserAccount account) {
        return ResponseEntity.ok(ApiResponse.success(adminEditionService.bulkJudge(request, username(account))));
    }

    private String username(CustomUserAccount account) {
        return account == null ? null : account.getUsername();
    }
}
