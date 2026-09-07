package com.test.test.sheetmusic.work.repository;

import com.test.test.sheetmusic.work.WorkAliasEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorkAliasRepository extends JpaRepository<WorkAliasEntity, Long> {

    @Query("select a from WorkAliasEntity a where a.work.id in :workIds order by a.id asc")
    List<WorkAliasEntity> findByWorkIds(@Param("workIds") List<Long> workIds);

    @Query("select count(distinct a.work.id) from WorkAliasEntity a where a.aliasNormalized = :normalized")
    long countWorksWithAlias(@Param("normalized") String normalized);

    @Query("select count(distinct a.work.id) from WorkAliasEntity a"
            + " where a.aliasNormalized = :normalized and a.work.id <> :excludeWorkId")
    long countOtherWorksWithAlias(@Param("normalized") String normalized, @Param("excludeWorkId") Long excludeWorkId);
}
