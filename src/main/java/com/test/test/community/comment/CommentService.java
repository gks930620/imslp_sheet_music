package com.test.test.community.comment;

import com.test.test.common.exception.AccessDeniedException;
import com.test.test.common.exception.EntityNotFoundException;
import com.test.test.community.CommunityEntity;
import com.test.test.community.comment.dto.CommentCreateDTO;
import com.test.test.community.comment.dto.CommentDTO;
import com.test.test.community.comment.dto.CommentUpdateDTO;
import com.test.test.community.comment.repository.CommentRepository;
import com.test.test.community.repository.CommunityRepository;
import com.test.test.jwt.entity.UserEntity;
import com.test.test.jwt.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommentService {

    private final CommentRepository commentRepository;
    private final CommunityRepository communityRepository;
    private final UserRepository userRepository;

    public Page<CommentDTO> getCommentsByCommunityId(Long communityId, Pageable pageable) {
        // 대상 게시글이 없거나 소프트삭제된 경우 404. (검증 없으면 삭제/부재 글의 댓글이 200으로 노출되어
        // 상세 API(404)와 비대칭이 되고, 삭제된 글의 댓글이 계속 조회됨). 엔티티 로드 없이 가벼운 존재 확인.
        if (!communityRepository.existsByIdAndIsDeletedFalse(communityId)) {
            throw EntityNotFoundException.of("게시글", communityId);
        }

        return commentRepository.findByCommunityIdWithUser(communityId, pageable)
                .map(CommentDTO::from);
    }

    @Transactional
    public CommentDTO createComment(Long communityId, CommentCreateDTO createDTO, String username) {
        CommunityEntity community = communityRepository.findByIdAndIsDeletedFalse(communityId)
                .orElseThrow(() -> EntityNotFoundException.of("게시글", communityId));

        UserEntity user = userRepository.findByUsername(username)
                .orElseThrow(() -> EntityNotFoundException.of("사용자", username));

        CommentEntity comment = createDTO.toEntity(community, user);
        return CommentDTO.from(commentRepository.save(comment));
    }

    @Transactional
    public CommentDTO updateComment(Long commentId, CommentUpdateDTO updateDTO, String username) {
        CommentEntity comment = findActiveCommentByIdAndValidateUser(commentId, username, true);
        comment.update(updateDTO.getContent());
        return CommentDTO.from(comment);
    }

    @Transactional
    public void deleteComment(Long commentId, String username) {
        CommentEntity comment = findActiveCommentByIdAndValidateUser(commentId, username, false);
        comment.softDelete();
    }

    /**
     * 삭제되지 않은 댓글을 조회하고 작성자 본인인지 검증한다.
     * @param forUpdate 권한 실패 메시지 분기(true=수정, false=삭제)
     */
    private CommentEntity findActiveCommentByIdAndValidateUser(Long commentId, String username, boolean forUpdate) {
        CommentEntity comment = commentRepository.findByIdAndIsDeletedFalse(commentId)
                .orElseThrow(() -> EntityNotFoundException.of("댓글", commentId));

        // 부모 게시글이 소프트삭제되었으면 수정/삭제도 막는다(조회 404와 대칭). soft-delete가 댓글로 cascade되지 않으므로 필요.
        CommunityEntity parent = comment.getCommunity();
        if (parent == null || Boolean.TRUE.equals(parent.getIsDeleted())) {
            throw EntityNotFoundException.of("게시글", parent != null ? parent.getId() : null);
        }

        if (!comment.isWrittenBy(username)) {
            throw forUpdate
                    ? AccessDeniedException.forUpdate("댓글")
                    : AccessDeniedException.forDelete("댓글");
        }

        return comment;
    }
}
