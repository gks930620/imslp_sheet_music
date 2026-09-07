package com.test.test.file.strategy;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

/**
 * 로컬 파일 저장 전략 (폴백).
 * - {@code app.bucket.endpoint} 가 비어 있을 때(로컬 개발/테스트) 활성화된다. → §5-3
 * - {@code ${FILE_UPLOAD_DIR:./uploads}} 디렉토리에 파일 저장.
 */
@Component
@Slf4j
@ConditionalOnExpression("'${app.bucket.endpoint:}'.trim().length() == 0")
public class LocalFileStorage implements FileStorageStrategy {

    @Value("${file.upload-dir:./uploads/}")
    private String uploadDir;

    @Override
    public FileUploadResult uploadFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("파일이 비어있습니다");
        }

        ensureUploadDirectoryExists();

        try {
            String originalFilename = file.getOriginalFilename();
            String extension = extractExtension(originalFilename);
            String storedFilename = generateUniqueFilename(extension);

            Path uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
            Path filePath = uploadPath.resolve(storedFilename);
            Files.write(filePath, file.getBytes());

            log.info("로컬 파일 저장 완료 - 원본: {}, 저장: {}, 경로: {}", originalFilename, storedFilename, filePath);

            return FileUploadResult.builder()
                    .originalFilename(originalFilename)
                    .storedFilename(storedFilename)
                    .filePath(filePath.toString())
                    .fileSize(file.getSize())
                    .contentType(file.getContentType())
                    .build();

        } catch (IOException e) {
            log.error("파일 저장 실패: {}", e.getMessage(), e);
            throw new RuntimeException("파일 저장 실패: " + file.getOriginalFilename(), e);
        }
    }

    @Override
    public FileUploadResult store(String originalFilename, String contentType, Path source) {
        ensureUploadDirectoryExists();
        try {
            String storedFilename = generateUniqueFilename(extractExtension(originalFilename));
            Path uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
            Path target = uploadPath.resolve(storedFilename);
            Files.copy(source, target);

            log.info("로컬 파일 저장 완료(임시 파일) - 원본: {}, 저장: {}", originalFilename, storedFilename);

            return FileUploadResult.builder()
                    .originalFilename(originalFilename)
                    .storedFilename(storedFilename)
                    .filePath(target.toString())
                    .fileSize(Files.size(target))
                    .contentType(contentType)
                    .build();
        } catch (IOException e) {
            log.error("파일 저장 실패: {}", e.getMessage(), e);
            throw new RuntimeException("파일 저장 실패: " + originalFilename, e);
        }
    }

    @Override
    public void deleteFile(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            return;
        }
        try {
            // DB에는 웹 경로(/uploads/xxx)가 저장되므로 파일명만 추출해 실제 업로드 디렉토리 기준으로 해석한다.
            String storedFilename = filePath.substring(filePath.lastIndexOf("/") + 1);
            Path uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
            Path path = uploadPath.resolve(storedFilename);
            if (Files.exists(path)) {
                Files.delete(path);
                log.info("로컬 파일 삭제 완료: {}", path);
            } else {
                log.warn("삭제할 로컬 파일이 없습니다: {}", path);
            }
        } catch (IOException e) {
            log.error("파일 삭제 실패: {}", e.getMessage(), e);
        }
    }

    @Override
    public Resource loadAsResource(String storedFileName) {
        try {
            Path uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
            Path file = uploadPath.resolve(storedFileName).normalize();

            // 경로 탈출(path traversal) 방어: 정규화 후에도 업로드 디렉토리 하위인지 확인
            if (!file.startsWith(uploadPath)) {
                log.warn("허용되지 않은 파일 경로 접근 시도: {}", storedFileName);
                return null;
            }

            Resource resource = new UrlResource(file.toUri());
            return (resource.exists() && resource.isReadable()) ? resource : null;
        } catch (MalformedURLException e) {
            log.warn("파일 경로 오류: {}", e.getMessage());
            return null;
        }
    }

    private void ensureUploadDirectoryExists() {
        try {
            Path uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
                log.info("업로드 디렉토리 생성: {}", uploadPath);
            }
        } catch (IOException e) {
            throw new RuntimeException("업로드 디렉토리 생성 실패", e);
        }
    }

    private String extractExtension(String filename) {
        if (filename != null && filename.contains(".")) {
            return filename.substring(filename.lastIndexOf("."));
        }
        return "";
    }

    private String generateUniqueFilename(String extension) {
        return UUID.randomUUID() + extension;
    }
}
