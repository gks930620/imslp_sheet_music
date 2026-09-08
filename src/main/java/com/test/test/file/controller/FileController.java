package com.test.test.file.controller;

import com.test.test.common.dto.ApiResponse;
import com.test.test.file.dto.FileDetailDTO;
import com.test.test.file.entity.FileEntity;
import com.test.test.file.entity.RefType;
import com.test.test.file.entity.Usage;
import com.test.test.file.service.FileService;
import com.test.test.file.strategy.FileStorageStrategy;
import com.test.test.jwt.model.CustomUserAccount;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 공용 파일 API (커뮤니티·사용자 파일 전용) — 02_API_명세서 §0-4.
 *
 * <p><b>바이트를 인라인으로 내주는 경로는 여기 없다.</b> 저장 파일명으로 바이트를 얻는 공개 경로는
 * {@code GET /uploads/{저장파일명}}({@link FileServingController}) 하나뿐이고, 판본 PDF 는 그마저도 404 다 —
 * 판본 바이트의 유일한 공개 경로는 저작권 게이트를 태우는 {@code GET /api/editions/{id}/download}(§3-4).
 * 보일러플레이트가 남긴 {@code GET /images/{filename}} 은 그 게이트를 거치지 않고 같은 바이트를 흘려
 * (qa 4차 실측: 비로그인 판본 PDF 200개·502MB) <b>제거했다</b>. 문이 늘면 게이트를 빠뜨린 문도 늘어난다 —
 * 인라인 서빙이 다시 필요해지면 새 경로를 만들지 말고 {@code /uploads} 를 쓴다.
 */
@RestController
@RequiredArgsConstructor
@Slf4j
public class FileController {
    private final FileService fileService;
    // 첨부 다운로드는 활성 저장전략(로컬 디스크 또는 버킷)에 위임한다(§5-3).
    private final FileStorageStrategy fileStorageStrategy;

    /**
     * 파일 조회 API (통합)
     * @param refId 참조 ID
     * @param refType COMMUNITY, USER
     * @param usage THUMBNAIL, IMAGES, ATTACHMENT (선택)
     * @return 파일 경로 리스트
     */
    @GetMapping("/api/files/paths")
    public ResponseEntity<ApiResponse<List<String>>> getFiles(
        @RequestParam Long refId,
        @RequestParam String refType,
        @RequestParam(required = false) String usage) {

        List<String> filePaths = fileService.getFilePaths(refId, refType, usage);
        return ResponseEntity.ok(ApiResponse.success("파일 경로 조회 성공", filePaths));
    }

    /**
     * 파일 업로드 API (공통)
     * @param files 업로드할 파일들
     * @param refId 참조 ID (리뷰 ID, 가게 ID 등)
     * @param refType COMMUNITY, USER
     * @param usage THUMBNAIL, IMAGES, ATTACHMENT
     * @return 업로드된 파일 경로 리스트
     */
    @PostMapping("/api/files")
    public ResponseEntity<ApiResponse<List<String>>> uploadFiles(
        @RequestParam("files") List<MultipartFile> files,
        @RequestParam Long refId,
        @RequestParam RefType refType,
        @RequestParam Usage usage,
        @AuthenticationPrincipal CustomUserAccount userAccount) {

        // 물리 저장 + DB 저장 + 소유권 검증을 서비스에 위임 (1 API = 1 Service 메서드)
        List<String> savedPaths = fileService.upload(files, refId, refType, usage, userAccount.getUsername());

        log.info("파일 업로드 완료 - refId: {}, refType: {}, 파일 수: {}", refId, refType, savedPaths.size());

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("파일 업로드 성공", savedPaths));
    }

    /**
     * 첨부파일 상세 정보 조회 API (원본 파일명 포함)
     * @param refId 참조 ID
     * @param refType COMMUNITY, USER
     * @param usage THUMBNAIL, IMAGES, ATTACHMENT (선택)
     * @return 파일 상세 정보 리스트
     */
    @GetMapping("/api/files")
    public ResponseEntity<ApiResponse<List<FileDetailDTO>>> getFileDetails(
        @RequestParam Long refId,
        @RequestParam String refType,
        @RequestParam(required = false) String usage) {

        List<FileDetailDTO> fileDetails = fileService.getFileDetails(refId, refType, usage);
        return ResponseEntity.ok(ApiResponse.success("파일 조회 성공", fileDetails));
    }

    /**
     * 첨부파일 다운로드 API
     * - 활성 저장전략에서 바이트를 읽어 원본 파일명으로 응답한다(로컬/버킷 공통).
     * @param fileId 파일 ID
     * @return 파일 리소스 (원본 파일명으로 다운로드)
     */
    @GetMapping("/api/files/{fileId}/content")
    public ResponseEntity<Resource> downloadFile(@PathVariable Long fileId) {
        FileEntity fileEntity = fileService.getFileById(fileId);
        if (fileEntity == null) {
            log.warn("파일을 찾을 수 없습니다: fileId={}", fileId);
            return ResponseEntity.notFound().build();
        }

        Resource resource = fileStorageStrategy.loadAsResource(fileEntity.getStoredFileName());
        if (resource == null || !resource.exists() || !resource.isReadable()) {
            log.warn("파일 바이트를 찾을 수 없습니다: fileId={}, stored={}", fileId, fileEntity.getStoredFileName());
            return ResponseEntity.notFound().build();
        }

        // 원본 파일명 인코딩 (한글 파일명 지정)
        String encodedFileName = URLEncoder.encode(fileEntity.getOriginalFileName(), StandardCharsets.UTF_8)
                .replaceAll("\\+", "%20");

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + encodedFileName + "\"")
                .body(resource);
    }

    /**
     * 파일 삭제 API
     * @param fileId 삭제할 파일 ID
     * @return 성공 시 삭제 완료 메시지
     */
    @DeleteMapping("/api/files/{fileId}")
    public ResponseEntity<ApiResponse<Void>> deleteFile(
        @PathVariable Long fileId,
        @AuthenticationPrincipal CustomUserAccount userAccount) {
        log.info("파일 삭제 요청: fileId={}", fileId);
        fileService.deleteFile(fileId, userAccount.getUsername());
        return ResponseEntity.ok(ApiResponse.success("파일이 삭제되었습니다."));
    }
}
