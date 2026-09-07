package com.test.test.community.comment.dto;

import com.test.test.community.CommunityEntity;
import com.test.test.community.comment.CommentEntity;
import com.test.test.jwt.entity.UserEntity;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CommentCreateDTO {

    @NotBlank(message = "Comment content is required")
    private String content;

    public CommentEntity toEntity(CommunityEntity community, UserEntity user) {
        return CommentEntity.builder()
                .community(community)
                .user(user)
                .content(this.content)
                .build();
    }
}
