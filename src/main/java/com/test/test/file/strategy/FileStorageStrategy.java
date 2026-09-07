package com.test.test.file.strategy;

import java.nio.file.Path;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

/**
 * 파일 저장 전략 인터페이스 (§5-3)
 * - 구현체: {@link LocalFileStorage}(로컬 디스크, 폴백), {@link BucketStorage}(Railway Storage Buckets / S3)
 * - 전략 선택은 {@code app.bucket.endpoint} 설정 유무로 결정된다(설정 있으면 버킷, 없으면 로컬).
 */
public interface FileStorageStrategy {

    /**
     * 파일 업로드
     * @param file 업로드할 파일
     * @return 저장 결과 (경로, 파일명 등)
     */
    FileUploadResult uploadFile(MultipartFile file);

    /**
     * 임시 파일(디스크)에 있는 바이트를 저장한다 (03 §2).
     * <p>크롤러가 받은 PDF·PDFBox 가 만든 PNG 는 {@link MultipartFile} 이 아니라서 이 진입점이 필요하다.
     * 원본 임시 파일은 호출자가 정리한다.
     *
     * @param originalFilename 원본 파일명(확장자 포함)
     * @param contentType      MIME 타입
     * @param source           읽어 올 임시 파일
     */
    FileUploadResult store(String originalFilename, String contentType, Path source);

    /**
     * 파일 삭제
     * @param filePath 삭제할 파일 경로 (/uploads/{저장파일명})
     */
    void deleteFile(String filePath);

    /**
     * 저장된 파일의 바이트를 스트리밍용 {@link Resource}로 읽어온다.
     * - 서빙 프록시({@code GET /uploads/{저장파일명}})와 다운로드({@code GET /api/files/{id}/content})에서 사용.
     * - 버킷은 private 이라 공개 URL이 없으므로 백엔드가 바이트를 프록시한다.
     * @param storedFileName 저장 파일명(= 버킷 key / 로컬 파일명)
     * @return 읽을 수 있는 Resource, 없으면 {@code null}
     */
    Resource loadAsResource(String storedFileName);

    /**
     * 파일 저장 결과 DTO
     */
    @lombok.Getter
    @lombok.Builder
    class FileUploadResult {
        private String originalFilename;  // 원본 파일명
        private String storedFilename;    // 저장된 파일명 (UUID) = 버킷 key / 로컬 파일명
        private String filePath;          // 내부 경로 (로컬: 절대경로 / 버킷: key) — 로그/삭제 참고용
        private Long fileSize;            // 파일 크기
        private String contentType;       // MIME 타입

        /**
         * DB(files.file_path)와 프론트에 노출되는 웹 경로.
         * 로컬/버킷 공통으로 {@code /uploads/{저장파일명}} 을 반환한다(버킷은 private → CDN URL 없음).
         * 서빙은 백엔드 프록시가 담당.
         */
        public String getWebPath() {
            return "/uploads/" + storedFilename;
        }
    }
}
