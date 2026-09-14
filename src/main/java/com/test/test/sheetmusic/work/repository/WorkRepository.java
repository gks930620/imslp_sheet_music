package com.test.test.sheetmusic.work.repository;

import com.test.test.sheetmusic.work.Section;
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

    /** §3-7 {@code workCount} — 그 구분의 공개 곡 수. 404 조건은 작곡가 존재 여부뿐이라 이 값이 0 이어도 200 이다(02 §0-7). */
    @Query("select count(w) from WorkEntity w"
            + " where w.composer.id = :composerId and w.hidden = false and w.section = :section")
    long countPublicByComposerIdAndSection(@Param("composerId") Long composerId, @Param("section") Section section);

    /** §3-3 {@code sameComposerWorks} — 그 곡의 구분 안에서만 고른다(기획 04 §5). */
    @Query("select w from WorkEntity w where w.composer.id = :composerId and w.hidden = false"
            + " and w.section = :section and w.id <> :excludeId"
            + " order by w.downloadCount desc, w.id asc")
    List<WorkEntity> findSameComposerWorks(@Param("composerId") Long composerId,
                                           @Param("excludeId") Long excludeId,
                                           @Param("section") Section section,
                                           Pageable pageable);

    // ===== 관리 홈 숫자 카드 (02 §4-1, 01_ERD §4 계산 규칙 — 숨김 포함) =====

    @Query("select count(w) from WorkEntity w left join w.recommendedEdition e"
            + " where e.pdfFileId is not null"
            + " and e.koreaCopyright = com.test.test.sheetmusic.edition.KoreaCopyright.FREE")
    long countReady();

    @Query("select count(w) from WorkEntity w left join w.recommendedEdition e"
            + " where e is null or e.pdfFileId is null")
    long countPreparing();

    /**
     * 그 구분에 공개 곡이 1개 이상인 작곡가 id 와 그 곡 수 (02 §3-1 작곡가 카드 · §3-5 · §3-6, §0-7).
     * 작곡가 엔티티를 통째로 로드하지 않는다 — 집계에 필요한 건 id 와 개수뿐이다.
     */
    @Query("select new com.test.test.sheetmusic.work.repository.ComposerWorkCount(w.composer.id, count(w))"
            + " from WorkEntity w where w.hidden = false and w.section = :section group by w.composer.id")
    List<ComposerWorkCount> countPublicWorksGroupedByComposer(@Param("section") Section section);

    /**
     * 지금 자동 판정을 되돌리면 <b>다운로드가 닫히는 곡 수</b> (02 §5-8-1 {@code revertibleRecommendedWorks}) —
     * 추천 판본이 "자동으로 열린 판본" 인 곡의 수다. 세는 애그리거트가 곡이라 여기에 둔다.
     *
     * <p><b>숨김 곡을 빼지 않는다.</b> §5-12 {@code recommendationKept} 가 빼지 않기 때문이다 —
     * 예고와 결과를 다른 모집단으로 세면 "지금 되돌리면 이렇게 된다" 는 말이 틀린다.
     * (§4-1 {@code needsRecommendationReviewWorks} 는 숨김을 빼지만, 그건 공개 기준을 재는 지표라 모집단이 다르다.)
     */
    @Query("select count(w) from WorkEntity w join w.recommendedEdition e"
            + " where e.copyrightJudgedBy = :judgedBy"
            + " and e.koreaCopyright = com.test.test.sheetmusic.edition.KoreaCopyright.FREE")
    long countAutoJudgedRecommendations(@Param("judgedBy") String judgedBy);
}
