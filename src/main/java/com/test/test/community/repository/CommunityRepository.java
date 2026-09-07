package com.test.test.community.repository;

import com.test.test.community.CommunityEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CommunityRepository extends JpaRepository<CommunityEntity, Long>, CommunityRepositoryCustom {

    /**
     * 삭제되지 않은 게시글 조회 (수정/삭제 시 사용)
     */
    Optional<CommunityEntity> findByIdAndIsDeletedFalse(Long id);

    /**
     * 삭제되지 않은 게시글 존재 여부 (엔티티 로드 없이 가벼운 존재 확인용)
     */
    boolean existsByIdAndIsDeletedFalse(Long id);

    /**
     * 조회수 원자적 증가. 인메모리 read-modify-write(`viewCount++`)는 동시 조회 시 증가가 유실되므로
     * DB에서 원자적으로 +1 한다. (clearAutomatically로 벌크 UPDATE 후 영속성 컨텍스트 정리)
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE CommunityEntity c SET c.viewCount = c.viewCount + 1 WHERE c.id = :id")
    void incrementViewCount(@Param("id") Long id);
}

