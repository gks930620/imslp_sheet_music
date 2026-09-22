package com.test.test.sheetmusic.recommendation.repository;

import com.test.test.sheetmusic.recommendation.WorkRecommendationLogEntity;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 추천 근거·이력 (01_ERD §3-13, 02 §4-7-2·§4-7-1). */
public interface WorkRecommendationLogRepository extends JpaRepository<WorkRecommendationLogEntity, Long> {

    List<WorkRecommendationLogEntity> findByWorkIdOrderByDecidedAtDescIdDesc(Long workId, Pageable pageable);

    long countByWorkId(Long workId);

    /**
     * 여러 곡의 <b>맨 위 줄 후보</b> — 곡별로 최신순이 되게 정렬해 한 번에 읽는다(02 §4-6 {@code recommendationSource}).
     * 판본 수와 달리(§4-6 {@code editionCount}) 집계 쿼리로 줄일 수 없어(맨 위 "행"이 필요하다),
     * 호출자가 워크아이디별 <b>첫 등장 행</b>만 취한다 — 그래도 20~200곡 규모에서 쿼리 1건이면 충분하다.
     */
    @Query("select l from WorkRecommendationLogEntity l where l.workId in :workIds"
            + " order by l.workId asc, l.decidedAt desc, l.id desc")
    List<WorkRecommendationLogEntity> findAllByWorkIdInOrderedForLatest(@Param("workIds") List<Long> workIds);

    /** 판본 삭제(§7) — 이 판본을 가리키던 <b>추천</b> 참조만 끊는다. 스냅샷 6개는 남는다. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update WorkRecommendationLogEntity l set l.editionId = null where l.editionId in :editionIds")
    void detachEditionRefs(@Param("editionIds") List<Long> editionIds);

    /** 판본 삭제(§7) — 이 판본을 가리키던 <b>직전 추천</b> 참조만 끊는다. 스냅샷 6개는 남는다. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update WorkRecommendationLogEntity l set l.previousEditionId = null where l.previousEditionId in :editionIds")
    void detachPreviousEditionRefs(@Param("editionIds") List<Long> editionIds);

    /** 곡 삭제(§7) — 곡이 없으면 설명할 대상이 없다. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from WorkRecommendationLogEntity l where l.workId = :workId")
    void deleteByWorkId(@Param("workId") Long workId);
}
