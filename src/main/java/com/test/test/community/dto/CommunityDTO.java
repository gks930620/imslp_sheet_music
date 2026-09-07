package com.test.test.community.dto;

import com.test.test.community.CommunityEntity;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 게시글 응답 (02 §5-3).
 *
 * <p>첨부·본문 이미지는 이 DTO 가 싣지 않는다 — 화면은 {@code /api/files?refId=&refType=COMMUNITY} 로 따로 받는다(03 §14-2).
 * 항상 빈 배열이던 {@code imageUrls}·{@code attachments} 는 같은 사실의 두 번째 출처가 되어 제거했다.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CommunityDTO {
    private Long id;
    private Long userId;
    private String username;
    private String nickname;
    private String title;
    private String content;
    private Integer viewCount;
    private Long commentCount;            // 댓글 수
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static CommunityDTO from(CommunityEntity entity) {
        if (entity == null) {
            return null;
        }
        return CommunityDTO.builder()
                .id(entity.getId())
                .userId(entity.getUser().getId())
                .username(entity.getUser().getUsername())
                .nickname(entity.getUser().getNickname())
                .title(entity.getTitle())
                .content(entity.getContent())
                .viewCount(entity.getViewCount())
                .commentCount(0L)  // 기본값, 실제 값은 Repository에서 설정
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
