package com.test.test.sheetmusic.work.repository;

import com.test.test.sheetmusic.work.WorkEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorkRepository extends JpaRepository<WorkEntity, Long>, WorkRepositoryCustom {

    Optional<WorkEntity> findByImslpUrl(String imslpUrl);

    long countByComposerId(Long composerId);

    @Query("select count(w) from WorkEntity w where w.composer.id = :composerId and w.hidden = false")
    long countPublicByComposerId(@Param("composerId") Long composerId);

    @Query("select w from WorkEntity w where w.composer.id = :composerId and w.hidden = false and w.id <> :excludeId"
            + " order by w.downloadCount desc, w.id asc")
    List<WorkEntity> findSameComposerWorks(@Param("composerId") Long composerId,
                                           @Param("excludeId") Long excludeId,
                                           Pageable pageable);

    @Query("select w from WorkEntity w where w.hidden = false"
            + " order by w.downloadCount desc, w.createdAt desc, w.id desc")
    List<WorkEntity> findPopular(Pageable pageable);

    // ===== 관리 홈 숫자 카드 (02 §4-1, 01_ERD §4 계산 규칙 — 숨김 포함) =====

    @Query("select count(w) from WorkEntity w left join w.recommendedEdition e"
            + " where e.pdfFileId is not null"
            + " and e.koreaCopyright = com.test.test.sheetmusic.edition.KoreaCopyright.FREE")
    long countReady();

    @Query("select count(w) from WorkEntity w left join w.recommendedEdition e"
            + " where e is null or e.pdfFileId is null")
    long countPreparing();

    @Query("select count(w) from WorkEntity w left join w.recommendedEdition e"
            + " where w.titleKo is null or w.level is null or e is null"
            + " or not exists (select 1 from WorkAliasEntity a where a.work = w)")
    long countNeedsWork();

    /**
     * 공개 곡이 1개 이상인 작곡가 id 와 그 곡 수 (02 §3-5·§3-6).
     * 작곡가 엔티티를 통째로 로드하지 않는다 — 집계에 필요한 건 id 와 개수뿐이다.
     */
    @Query("select new com.test.test.sheetmusic.work.repository.ComposerWorkCount(w.composer.id, count(w))"
            + " from WorkEntity w where w.hidden = false group by w.composer.id")
    List<ComposerWorkCount> countPublicWorksGroupedByComposer();
}
