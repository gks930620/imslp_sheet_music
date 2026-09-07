package com.test.test.sheetmusic.edition.repository;

import com.test.test.sheetmusic.edition.EditionEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EditionRepository extends JpaRepository<EditionEntity, Long>, EditionRepositoryCustom {

    List<EditionEntity> findByWorkIdOrderByIdAsc(Long workId);

    Optional<EditionEntity> findByImslpFileId(String imslpFileId);

    @Query("select e from EditionEntity e where e.work.id in :workIds")
    List<EditionEntity> findByWorkIds(@Param("workIds") List<Long> workIds);

    @Query("select count(e) from EditionEntity e"
            + " where e.koreaCopyright = com.test.test.sheetmusic.edition.KoreaCopyright.UNKNOWN")
    long countUnknownCopyright();

    @Query("select count(e) from EditionEntity e where e.work.id = :workId and e.imslpFileId is not null")
    long countCrawledEditions(@Param("workId") Long workId);
}
