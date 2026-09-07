package com.test.test.file.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "files")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FileEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String originalFileName;

    @Column(nullable = false)
    private String storedFileName;

    @Column(nullable = false)
    private String filePath;

    private Long fileSize;

    private String contentType;

    private Long refId;

    // 업로더(username). 아직 리소스에 연결되지 않은 임시(refId=0) 파일의 소유권 판별용.
    // 정식 refId를 가진 파일은 참조 리소스(게시글/사용자)에서 소유권이 파생되므로 보조 용도.
    private String uploadedBy;

    @Enumerated(EnumType.STRING)
    private RefType refType;

    @Column(name = "file_usage")
    @Enumerated(EnumType.STRING)
    private Usage fileUsage;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // ===== 도메인 메서드 (§1: 서비스에서 setter 직접 조작 금지) =====

    /**
     * 파일을 특정 리소스(글)에 연결한다. (본문 이미지 reconcile — §5-3-1 ①)
     * @param refId 연결할 리소스 ID (글 ID)
     */
    public void linkTo(Long refId) {
        this.refId = refId;
    }

    /**
     * 파일 연결을 해제해 임시(orphan 후보) 상태로 되돌린다. (본문에서 빠진 이미지 — §5-3-1 ①)
     * refId=0 은 "어떤 글에도 연결되지 않음"을 뜻하며, 유예시간 경과 시 orphan 배치가 정리한다(③).
     */
    public void unlink() {
        this.refId = 0L;
    }
}
