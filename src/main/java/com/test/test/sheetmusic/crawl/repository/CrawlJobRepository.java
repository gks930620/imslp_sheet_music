package com.test.test.sheetmusic.crawl.repository;

import com.test.test.sheetmusic.crawl.CrawlJobEntity;
import com.test.test.sheetmusic.crawl.CrawlJobStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CrawlJobRepository extends JpaRepository<CrawlJobEntity, Long> {

    @Query("select j from CrawlJobEntity j where j.status in :statuses order by j.id desc")
    List<CrawlJobEntity> findByStatuses(@Param("statuses") List<CrawlJobStatus> statuses);

    Page<CrawlJobEntity> findAllByOrderByCreatedAtDescIdDesc(Pageable pageable);

    Optional<CrawlJobEntity> findFirstByOrderByCreatedAtDescIdDesc();
}
