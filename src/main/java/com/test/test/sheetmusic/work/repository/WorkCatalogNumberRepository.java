package com.test.test.sheetmusic.work.repository;

import com.test.test.sheetmusic.work.WorkCatalogNumberEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorkCatalogNumberRepository extends JpaRepository<WorkCatalogNumberEntity, Long> {

    @Query("select c from WorkCatalogNumberEntity c where c.work.id in :workIds order by c.sortOrder asc, c.id asc")
    List<WorkCatalogNumberEntity> findByWorkIds(@Param("workIds") List<Long> workIds);
}
