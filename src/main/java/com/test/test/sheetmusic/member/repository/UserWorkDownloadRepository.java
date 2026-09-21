package com.test.test.sheetmusic.member.repository;

import com.test.test.sheetmusic.member.UserWorkDownloadEntity;
import com.test.test.sheetmusic.work.Section;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserWorkDownloadRepository extends JpaRepository<UserWorkDownloadEntity, Long> {

    /** upsert 의 조회 절반 — {@code uk_user_work_download(user_id, work_id)} 가 한 줄을 보장한다. */
    Optional<UserWorkDownloadEntity> findByUserIdAndWorkId(Long userId, Long workId);

    /**
     * <b>처음 받는 곡</b> 한 갈래의 넣기 — 한 문장이라 "있나 보고 넣는" 창이 없다 (03 §26-2, 02 §3-4).
     *
     * <p>이 자리에만 네이티브 SQL 을 쓰는 이유: 겹친 요청이 {@code uk_user_work_download} 를 위반하면
     * 다운로드 본체 트랜잭션이 rollback-only 가 돼 <b>집계까지</b> 함께 사라진다. 본체 트랜잭션 안
     * ({@code MANDATORY})이라 즐겨찾기처럼 쓰기를 떼어낼 수 없으므로(§26-1), 위반을 "처리" 하는 대신
     * <b>일어나지 않게</b> 한다. 근거는 {@link com.test.test.sheetmusic.member.MyLibraryRecorder} javadoc.
     *
     * <p><b>{@code created_at} 은 INSERT 에서만</b> 쓴다 — 갱신 목록에 넣으면 "처음 받은 시각" 이 매번
     * 덮여 거짓이 된다(01_ERD §3-12). 갱신 값은 {@code VALUES(col)} 같은 MySQL 전용 함수 대신
     * <b>파라미터로 다시 넘긴다</b> — 그래야 H2(MODE=MySQL)·MySQL 에서 문장이 똑같다.
     *
     * <p><b>스키마 드리프트 주의</b>(03 §17-5): {@code user_work_download} 에 컬럼을 더하거나 이름을 바꾸면
     * 이 문장도 함께 고쳐야 한다 — 네이티브 SQL 은 컴파일 타임에 걸리지 않는다.
     *
     * <p><b>여기만 {@code clearAutomatically} 가 없는 이유</b>(아래 형제 둘에는 붙어 있다 — 빠뜨린 것이
     * 아니다): ⑴ 이 문장은 {@code findByUserIdAndWorkId} 가 <b>비어 있을 때만</b> 불린다
     * ({@link com.test.test.sheetmusic.member.MyLibraryRecorder}) — 그 행의 관리 사본이 애초에 없으니
     * 떼어낼 stale 이 없다. ⑵ {@code clear} 는 이 행이 아니라 <b>영속성 컨텍스트 전체</b>를 떼어내서,
     * 이 호출 뒤에 코드가 한 줄이라도 붙는 날 그 변경이 <b>조용히</b> 사라진다. 반면
     * {@link #detachEditions}·{@link #deleteByWorkId} 는 <b>여러 행을 한 번에 바꾸는 벌크</b>라
     * 이미 읽어 둔 관리 사본이 있을 수 있고, 그래서 그쪽에는 붙어 있다.
     *
     * @param lastDownloadedAt 이하 스냅샷 파라미터는 {@code UserWorkDownloadEntity} 가 판본에서 계산한 값이다
     *                         (계산 규칙이 SQL 로 새지 않게 — 03 §26-2)
     */
    @Modifying(flushAutomatically = true)
    @Query(value = "insert into user_work_download"
            + " (user_id, work_id, last_downloaded_at, last_edition_id, edition_kind, edition_scope,"
            + " edition_movement_number, edition_page_count, edition_file_size, created_at)"
            + " values (:userId, :workId, :lastDownloadedAt, :lastEditionId, :editionKind, :editionScope,"
            + " :editionMovementNumber, :editionPageCount, :editionFileSize, :createdAt)"
            + " on duplicate key update"
            + " last_downloaded_at = :lastDownloadedAt, last_edition_id = :lastEditionId,"
            + " edition_kind = :editionKind, edition_scope = :editionScope,"
            + " edition_movement_number = :editionMovementNumber, edition_page_count = :editionPageCount,"
            + " edition_file_size = :editionFileSize",
            nativeQuery = true)
    void upsert(@Param("userId") Long userId,
                @Param("workId") Long workId,
                @Param("lastDownloadedAt") Instant lastDownloadedAt,
                @Param("lastEditionId") Long lastEditionId,
                @Param("editionKind") String editionKind,
                @Param("editionScope") String editionScope,
                @Param("editionMovementNumber") Integer editionMovementNumber,
                @Param("editionPageCount") Integer editionPageCount,
                @Param("editionFileSize") Long editionFileSize,
                @Param("createdAt") Instant createdAt);

    /**
     * 받은 악보 탭 (02 §10-3) — 정렬 {@code last_downloaded_at DESC, id DESC}, 그 구분의 숨김 아닌 곡만.
     *
     * <p><b>한 페이지의 줄만</b> 돌려준다 — {@code Page} 가 아니다. 총 개수는 {@link #countVisible} 하나가
     * 말하고({@code counts}), {@code MyLibraryQueryService} 가 그 값으로 페이지를 감싼다(03 §27).
     * 여기에 {@code countQuery} 를 붙이면 같은 수를 세는 곳이 둘이 돼, 숨김·구분 조건을 한쪽만 고치는 날
     * 응답은 200 인 채 {@code counts} 와 {@code totalElements} 만 조용히 갈린다(02 §10-2 는 "항상 같다" 가 계약).
     */
    @Query("select u from UserWorkDownloadEntity u join u.work w"
            + " where u.user.id = :userId and w.hidden = false and w.section = :section"
            + " order by u.lastDownloadedAt desc, u.id desc")
    List<UserWorkDownloadEntity> findReceived(@Param("userId") Long userId,
                                              @Param("section") Section section,
                                              Pageable pageable);

    @Query("select count(u) from UserWorkDownloadEntity u join u.work w"
            + " where u.user.id = :userId and w.hidden = false and w.section = :section")
    long countVisible(@Param("userId") Long userId, @Param("section") Section section);

    /**
     * 판본 삭제 (01_ERD §7) — 줄은 남기고 판본 참조만 끊는다.
     * <b>스냅샷 5개는 지우지 않는다</b>: 그래야 "그때 받은 악보는 지금 받을 수 없어요" 아래에
     * 무엇을 못 주는지 적을 수 있다(화면정의 09 §6 S4).
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update UserWorkDownloadEntity u set u.lastEdition = null where u.lastEdition.id in :editionIds")
    void detachEditions(@Param("editionIds") List<Long> editionIds);

    /** 곡 삭제 (01_ERD §7). */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from UserWorkDownloadEntity u where u.work.id = :workId")
    void deleteByWorkId(@Param("workId") Long workId);
}
