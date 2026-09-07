package com.test.test.file.controller;

import com.test.test.file.strategy.FileStorageStrategy;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * 업로드 파일 서빙 프록시 (§5-3).
 * <p>버킷은 private → 공개 URL이 없으므로 백엔드가 활성 저장전략({@link FileStorageStrategy})에서
 * 바이트를 읽어 스트리밍한다. 프론트 계약 {@code GET /uploads/{저장파일명}} 은 로컬/버킷 모드와 무관하게
 * 동일하게 동작한다(같은 오리진 → presigned 만료 걱정 없음).
 */
@RestController
@RequiredArgsConstructor
public class FileServingController {

    private final FileStorageStrategy fileStorageStrategy;

    @GetMapping("/uploads/{storedFileName:.+}")
    public ResponseEntity<Resource> serve(@PathVariable String storedFileName) {
        Resource resource = fileStorageStrategy.loadAsResource(storedFileName);
        if (resource == null || !resource.exists() || !resource.isReadable()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(guessMediaType(storedFileName))
                .body(resource);
    }

    /** 저장 파일명 확장자로 Content-Type 추정 (인라인 렌더용). 알 수 없으면 octet-stream. */
    private MediaType guessMediaType(String filename) {
        String lower = filename.toLowerCase();
        if (lower.endsWith(".png")) return MediaType.IMAGE_PNG;
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return MediaType.IMAGE_JPEG;
        if (lower.endsWith(".gif")) return MediaType.IMAGE_GIF;
        if (lower.endsWith(".webp")) return MediaType.parseMediaType("image/webp");
        if (lower.endsWith(".pdf")) return MediaType.APPLICATION_PDF;
        // svg는 인라인 렌더 시 XSS 위험이 있어 image/* 로 매핑하지 않고 기본값 유지
        return MediaType.APPLICATION_OCTET_STREAM;
    }
}
