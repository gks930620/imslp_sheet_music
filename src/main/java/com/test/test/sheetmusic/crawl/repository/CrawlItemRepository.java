package com.test.test.sheetmusic.crawl.repository;

import com.test.test.sheetmusic.crawl.CrawlItemEntity;
import com.test.test.sheetmusic.crawl.CrawlItemStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CrawlItemRepository extends JpaRepository<CrawlItemEntity, Long> {

    List<CrawlItemEntity> findByJobIdOrderBySeqAsc(Long jobId);

    List<CrawlItemEntity> findByJobIdAndStatusOrderBySeqAsc(Long jobId, CrawlItemStatus status);

    Optional<CrawlItemEntity> findFirstByJobIdAndStatusOrderBySeqAsc(Long jobId, CrawlItemStatus status);

    long countByJobIdAndStatus(Long jobId, CrawlItemStatus status);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update CrawlItemEntity i set i.workId = null where i.workId = :workId")
    void detachWork(@Param("workId") Long workId);

    @Query("select i from CrawlItemEntity i"
            + " where i.status = com.test.test.sheetmusic.crawl.CrawlItemStatus.PROCESSING")
    List<CrawlItemEntity> findProcessing();
}
