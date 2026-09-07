package com.test.test.community;

import com.test.test.jwt.entity.UserEntity;
import com.test.test.common.exception.DuplicateResourceException;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "community")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CommunityEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 작성자 (N:1 - 여러 게시글은 한 사용자에게 속함)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(nullable = false)
    @Builder.Default
    private Integer viewCount = 0;

    // 삭제 여부
    @Column(nullable = false)
    @Builder.Default
    private Boolean isDeleted = false;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // ===== 비즈니스 메서드 =====
    // (조회수 증가는 lost update 방지를 위해 CommunityRepository.incrementViewCount 원자 쿼리로 처리)

    /**
     * 게시글 수정
     */
    public void update(String title, String content) {
        this.title = title;
        this.content = content;
    }

    /**
     * 게시글 소프트 삭제
     */
    public void softDelete() {
        if (this.isDeleted) {
            throw DuplicateResourceException.alreadyDeleted("게시글");
        }
        this.isDeleted = true;
    }

    /**
     * 작성자 확인
     */
    public boolean isWrittenBy(String username) {
        return this.user != null && this.user.getUsername().equals(username);
    }
}

