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

@RestController
@RequiredArgsConstructor
@Slf4j
public class FileController {
    private final FileService fileService;
    // 서빙/다운로드는 활성 저장전략(로컬 디스크 또는 버킷)에 위임한다(§5-3).
    private final FileStorageStrategy fileStorageStrategy;

    /**
     * 이미지 파일 서비스 (HTML img 태그에서 호출)
     * - 활성 저장전략에서 바이트를 읽어 인라인 렌더용으로 서빙한다.
     */
    @GetMapping("/images/{filename:.+}")
    public ResponseEntity<Resource> serveFile(@PathVariable String filename) {
        Resource resource = fileStorageStrategy.loadAsResource(filename);
        if (resource == null || !resource.exists() || !resource.isReadable()) {
            return ResponseEntity.notFound().build();
        }
        String contentType = "image/jpeg"; // 기본값
        String lower = filename.toLowerCase();
        if (lower.endsWith(".png")) contentType = "image/png";
        else if (lower.endsWith(".gif")) contentType = "image/gif";
        else if (lower.endsWith(".webp")) contentType = "image/webp";
        // svg는 인라인 렌더 시 XSS 위험이 있어 image/* 로 매핑하지 않고 기본값 유지

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .body(resource);
    }

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
