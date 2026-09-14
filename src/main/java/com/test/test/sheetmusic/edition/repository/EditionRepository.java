package com.test.test.sheetmusic.edition.repository;

import com.test.test.sheetmusic.edition.EditionEntity;
import com.test.test.sheetmusic.edition.FileFetchStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
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

    /**
     * 자동 판정 대상의 <b>id 만</b> (02 §5-11, 03 §16-1): {@code UNKNOWN} 인 모든 판본(파일 유무 무관).
     * 엔티티는 청크 단위 트랜잭션에서 {@link #findUnknownCopyrightForAutoJudge(List)} 로 나눠 읽는다 —
     * 대상이 수천~수만 건이라 한 트랜잭션에서 다 잡으면 수집 워커의 짧은 UPDATE 와 락 경합을 일으킨다.
     */
    @Query("select e.id from EditionEntity e"
            + " where e.koreaCopyright = com.test.test.sheetmusic.edition.KoreaCopyright.UNKNOWN"
            + " order by e.id asc")
    List<Long> findUnknownCopyrightIdsForAutoJudge();

    /**
     * 자동 판정 청크 (02 §5-11). 판정에 작곡가 사망 연도가 필요하므로 곡·작곡가를 함께 읽는다(N+1 방지).
     * id 목록을 뽑은 뒤 다른 관리자가 판정해 버린 판본은 여기서 자연히 빠진다(여전히 UNKNOWN 인 것만 읽는다).
     */
    @Query("select e from EditionEntity e join fetch e.work w join fetch w.composer"
            + " where e.id in :ids"
            + " and e.koreaCopyright = com.test.test.sheetmusic.edition.KoreaCopyright.UNKNOWN"
            + " order by e.id asc")
    List<EditionEntity> findUnknownCopyrightForAutoJudge(@Param("ids") List<Long> ids);

    /** 재시작 복구 (02 §5-7) — '받아오는 중'에 갇힌 판본. */
    @Query("select e from EditionEntity e where e.fileFetchStatus in :statuses order by e.id asc")
    List<EditionEntity> findByFileFetchStatuses(
            @Param("statuses") Collection<FileFetchStatus> statuses);

    /**
     * 추천 후보 1건 (01_ERD §3-3, 02 §4-7 {@code isCandidate}): 그 곡의 전체 악보·전곡·파일 있는 판본 중
     * IMSLP 다운로드 수 최대(없으면 뒤로), 동률이면 id 최소. 판본 상세 응답을 만들 때마다 그 곡 판본을
     * 전량 읽지 않기 위한 조회다(곡당 판본 70개 — 03 §16-1).
     */
    @Query("select e from EditionEntity e where e.work.id = :workId and e.pdfFileId is not null"
            + " and e.kind = com.test.test.sheetmusic.edition.EditionKind.COMPLETE_SCORE"
            + " and e.scope = com.test.test.sheetmusic.edition.EditionScope.COMPLETE"
            + " order by e.imslpDownloadCount desc nulls last, e.id asc")
    List<EditionEntity> findCandidates(@Param("workId") Long workId, Pageable pageable);

    /** 곡 목록(§4-6)의 판본 수 — 숫자 하나 때문에 판본 행을 전부 읽지 않는다(03 §16-1). */
    @Query("select e.work.id as workId, count(e) as editionCount from EditionEntity e"
            + " where e.work.id in :workIds group by e.work.id")
    List<WorkEditionCount> countByWorkIds(@Param("workIds") List<Long> workIds);

    /**
     * 추천 자동 지정 후보 (02 §5-11): 추천이 <b>없는</b> 곡의 전체 악보·전곡·파일 있는 판본.
     * 판정 결과는 이 트랜잭션 안에서 막 바뀌었을 수 있어 {@code korea_copyright} 는 쿼리로 거르지 않고
     * 호출자가 "이번에 FREE 가 된 것 + 원래 FREE 인 것" 으로 판단한다(dryRun 도 같은 경로를 쓴다).
     */
    @Query("select e from EditionEntity e join fetch e.work w"
            + " where w.recommendedEdition is null and e.pdfFileId is not null"
            + " and e.kind = com.test.test.sheetmusic.edition.EditionKind.COMPLETE_SCORE"
            + " and e.scope = com.test.test.sheetmusic.edition.EditionScope.COMPLETE")
    List<EditionEntity> findRecommendCandidates();

    /**
     * 자동 판정 되돌리기 대상 (02 §5-12). 사람이 다시 판정한 판본은 {@code copyrightJudgedBy} 가
     * 그 관리자 username 으로 바뀌어 있으므로 자동으로 빠진다.
     */
    @Query("select e from EditionEntity e where e.copyrightJudgedBy = :judgedBy"
            + " and e.koreaCopyright = com.test.test.sheetmusic.edition.KoreaCopyright.FREE")
    List<EditionEntity> findAutoJudged(@Param("judgedBy") String judgedBy);

    /**
     * 되돌릴 수 있는 판본 수 (02 §5-8-1 {@code revertibleEditions}). {@link #findAutoJudged(String)} 와
     * <b>같은 조건</b>이라 예고(§5-8)와 실제 되돌리기(§5-12) 결과가 어긋날 수 없다.
     * 대기함 조회 한 번마다 판본 행을 수천 건 읽지 않도록 개수만 센다
     * ({@code idx_edition_korea_copyright} 로 충분해 인덱스를 더 두지 않는다 — ERD 변경 없음).
     */
    @Query("select count(e) from EditionEntity e where e.copyrightJudgedBy = :judgedBy"
            + " and e.koreaCopyright = com.test.test.sheetmusic.edition.KoreaCopyright.FREE")
    long countAutoJudged(@Param("judgedBy") String judgedBy);
}
