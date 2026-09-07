package com.test.test.sheetmusic.composer.repository;

import com.test.test.sheetmusic.composer.ComposerAliasEntity;
import com.test.test.sheetmusic.composer.ComposerEntity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ComposerRepository extends JpaRepository<ComposerEntity, Long>, ComposerRepositoryCustom {

    Optional<ComposerEntity> findByNameOriginalNormalized(String nameOriginalNormalized);

    /** 곡 목록 조립용 배치(IN) 로딩 — 곡마다 {@code composer.getAliases()} 를 건드리면 N+1 이다(03 §7 3번). */
    @Query("select a from ComposerAliasEntity a where a.composer.id in :composerIds order by a.id asc")
    List<ComposerAliasEntity> findAliasesByComposerIds(@Param("composerIds") Collection<Long> composerIds);
}
