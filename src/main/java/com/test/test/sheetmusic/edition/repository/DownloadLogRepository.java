package com.test.test.sheetmusic.edition.repository;

import com.test.test.sheetmusic.edition.DownloadLogEntity;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DownloadLogRepository extends JpaRepository<DownloadLogEntity, Long> {

    boolean existsByWorkId(Long workId);

    @Query("select count(d) from DownloadLogEntity d where d.downloadedAt >= :from")
    long countSince(@Param("from") Instant from);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from DownloadLogEntity d where d.workId = :workId")
    void deleteByWorkId(@Param("workId") Long workId);

    /**
     * 판본 삭제(02 §5-5) — 로그는 남기고 판본 참조만 끊는다. 로그는 <b>곡</b> 단위 사실이라
     * 판본을 지웠다고 지울 수 없고(인기곡·대시보드가 과거를 다시 쓰게 된다), 그렇다고 사라진 판본을
     * 계속 가리키게 두면 매달린 참조가 된다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update DownloadLogEntity d set d.editionId = null where d.editionId in :editionIds")
    void detachEditions(@Param("editionIds") List<Long> editionIds);
}
