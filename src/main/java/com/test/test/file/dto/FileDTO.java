package com.test.test.file.dto;

import com.test.test.file.entity.FileEntity;
import com.test.test.file.entity.RefType;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FileDTO {

    private Long id;
    private String originalFileName;
    private String storedFileName;
    private String filePath;
    private Long fileSize;
    private String contentType;
    private Long refId;
    private RefType refType;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static FileDTO from(FileEntity entity) {
        return FileDTO.builder()
                .id(entity.getId())
                .originalFileName(entity.getOriginalFileName())
                .storedFileName(entity.getStoredFileName())
                .filePath(entity.getFilePath())
                .fileSize(entity.getFileSize())
                .contentType(entity.getContentType())
                .refId(entity.getRefId())
                .refType(entity.getRefType())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    public FileEntity toEntity() {
        return FileEntity.builder()
                .id(this.id)
                .originalFileName(this.originalFileName)
                .storedFileName(this.storedFileName)
                .filePath(this.filePath)
                .fileSize(this.fileSize)
                .contentType(this.contentType)
                .refId(this.refId)
                .refType(this.refType)
                .build();
    }
}
