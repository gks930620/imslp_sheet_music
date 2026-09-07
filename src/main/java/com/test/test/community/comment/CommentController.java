package com.test.test.community.comment;

import com.test.test.common.dto.ApiResponse;
import com.test.test.common.dto.PageResponse;
import com.test.test.community.comment.dto.CommentCreateDTO;
import com.test.test.community.comment.dto.CommentDTO;
import com.test.test.community.comment.dto.CommentUpdateDTO;
import com.test.test.jwt.model.CustomUserAccount;
import jakarta.validation.Valid;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    @GetMapping("/communities/{communityId}/comments")
    public ResponseEntity<ApiResponse<PageResponse<CommentDTO>>> getComments(
            @PathVariable Long communityId,
            @PageableDefault(size = 10, sort = "createdAt") Pageable pageable) {

        Page<CommentDTO> comments = commentService.getCommentsByCommunityId(communityId, pageable);
        return ResponseEntity.ok(ApiResponse.success("Comments fetched", PageResponse.from(comments)));
    }

    @PostMapping("/communities/{communityId}/comments")
    public ResponseEntity<ApiResponse<CommentDTO>> createComment(
            @PathVariable Long communityId,
            @Valid @RequestBody CommentCreateDTO createDTO,
            @AuthenticationPrincipal CustomUserAccount userAccount) {

        CommentDTO comment = commentService.createComment(communityId, createDTO, userAccount.getUsername());
        return ResponseEntity.created(URI.create("/api/comments/" + comment.getId()))
                .body(ApiResponse.success("Comment created", comment));
    }

    @PutMapping("/comments/{commentId}")
    public ResponseEntity<ApiResponse<CommentDTO>> updateComment(
            @PathVariable Long commentId,
            @Valid @RequestBody CommentUpdateDTO updateDTO,
            @AuthenticationPrincipal CustomUserAccount userAccount) {

        CommentDTO comment = commentService.updateComment(commentId, updateDTO, userAccount.getUsername());
        return ResponseEntity.ok(ApiResponse.success("Comment updated", comment));
    }

    @DeleteMapping("/comments/{commentId}")
    public ResponseEntity<ApiResponse<Void>> deleteComment(
            @PathVariable Long commentId,
            @AuthenticationPrincipal CustomUserAccount userAccount) {
        commentService.deleteComment(commentId, userAccount.getUsername());
        return ResponseEntity.ok(ApiResponse.success("Comment deleted"));
    }
}
