package com.test.test.sheetmusic.seed.repository;

import com.test.test.sheetmusic.seed.SeedLoadEntity;
import com.test.test.sheetmusic.seed.SeedType;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SeedLoadRepository extends JpaRepository<SeedLoadEntity, Long> {

    /** 이미 적재한 자연키 — CSV 행 수만큼 쿼리를 날리지 않도록 종류별로 한 번에 읽는다. */
    @Query("select s.naturalKey from SeedLoadEntity s where s.seedType = :seedType")
    List<String> findNaturalKeys(@Param("seedType") SeedType seedType);
}
