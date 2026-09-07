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

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from DownloadLogEntity d where d.editionId in :editionIds")
    void deleteByEditionIds(@Param("editionIds") List<Long> editionIds);
}
