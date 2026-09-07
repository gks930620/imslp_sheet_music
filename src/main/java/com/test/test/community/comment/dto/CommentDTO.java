package com.test.test.community.comment.dto;

import com.test.test.community.comment.CommentEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CommentDTO {

    private Long id;
    private Long communityId;
    private String content;

    // 작성자 정보
    private Long userId;
    private String username;
    private String nickname;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * Entity -> DTO 변환
     */
    public static CommentDTO from(CommentEntity entity) {
        return CommentDTO.builder()
                .id(entity.getId())
                .communityId(entity.getCommunity().getId())
                .content(entity.getContent())
                .userId(entity.getUser().getId())
                .username(entity.getUser().getUsername())
                .nickname(entity.getUser().getNickname())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}

