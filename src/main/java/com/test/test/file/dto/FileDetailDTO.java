package com.test.test.file.dto;

import com.test.test.file.entity.FileEntity;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FileDetailDTO {
    private Long fileId;
    private String originalFileName;
    private String storedFileName;
    private Long fileSize;
    private String downloadUrl;  // 다운로드용 (원본 파일명 필요 → 서버 API 유지)
    private String previewUrl;   // 미리보기용 (/uploads/{저장파일명} 프록시)

    public static FileDetailDTO from(FileEntity entity) {
        return FileDetailDTO.builder()
                .fileId(entity.getId())
                .originalFileName(entity.getOriginalFileName())
                .storedFileName(entity.getStoredFileName())
                .fileSize(entity.getFileSize())
                // 다운로드: 원본 파일명이 필요하므로 서버 API 경유
                .downloadUrl("/api/files/" + entity.getId() + "/content")
                // 미리보기: DB에 저장된 웹 경로(/uploads/{저장파일명}) — 서빙 프록시가 처리
                .previewUrl(entity.getFilePath())
                .build();
    }
}

