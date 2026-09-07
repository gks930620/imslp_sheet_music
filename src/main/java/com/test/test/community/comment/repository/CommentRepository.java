package com.test.test.community.comment.repository;

import com.test.test.community.comment.CommentEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
public interface CommentRepository extends JpaRepository<CommentEntity, Long> {

    /**
     * 삭제되지 않은 댓글만 단건 조회 (수정/삭제 권한 검증용).
     * 소프트삭제된 댓글이 수정되는 것을 방지한다.
     */
    Optional<CommentEntity> findByIdAndIsDeletedFalse(Long id);

    /**
     * 특정 게시글의 삭제되지 않은 댓글 수 (상세 조회에서 commentCount 세팅용).
     */
    long countByCommunityIdAndIsDeletedFalse(Long communityId);

    /**
     * 특정 게시글의 댓글 목록 조회 (삭제되지 않은 것만, User fetch join으로 N+1 방지, 페이징)
     */
    @Query(value = "SELECT c FROM CommentEntity c " +
           "JOIN FETCH c.user " +
           "WHERE c.community.id = :communityId " +
           "AND c.isDeleted = false " +
           "ORDER BY c.createdAt DESC",
           countQuery = "SELECT count(c) FROM CommentEntity c WHERE c.community.id = :communityId AND c.isDeleted = false")
    Page<CommentEntity> findByCommunityIdWithUser(@Param("communityId") Long communityId, Pageable pageable);


    /**
     * 여러 게시글의 댓글 개수를 한 번에 조회 (IN 쿼리로 N+1 방지)
     */
    @Query("SELECT c.community.id, COUNT(c) FROM CommentEntity c " +
           "WHERE c.community.id IN :communityIds " +
           "AND c.isDeleted = false " +
           "GROUP BY c.community.id")
    List<Object[]> countByCommunityIdsGrouped(@Param("communityIds") List<Long> communityIds);

    /**
     * 여러 게시글의 댓글 개수를 Map으로 반환 (편의 메서드)
     */
    default Map<Long, Long> countByCommunityIdsAsMap(List<Long> communityIds) {
        if (communityIds == null || communityIds.isEmpty()) {
            return Map.of();
        }

        return countByCommunityIdsGrouped(communityIds).stream()
                .collect(Collectors.toMap(
                        result -> (Long) result[0],  // community.id
                        result -> (Long) result[1]   // count
                ));
    }
}

