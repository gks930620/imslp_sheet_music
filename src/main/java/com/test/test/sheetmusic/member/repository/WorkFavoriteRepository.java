package com.test.test.sheetmusic.member.repository;

import com.test.test.sheetmusic.member.WorkFavoriteEntity;
import com.test.test.sheetmusic.work.Section;
import com.test.test.sheetmusic.work.WorkEntity;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorkFavoriteRepository extends JpaRepository<WorkFavoriteEntity, Long> {

    /** 곡 상세의 {@code favorited}(02 §3-3)와 켜기 멱등 판정(§10-1)이 같이 쓴다. */
    boolean existsByUserIdAndWorkId(Long userId, Long workId);

    /** 끄기(§10-1) — 없던 것을 꺼도 오류가 아니므로 결과를 보지 않는다. */
    void deleteByUserIdAndWorkId(Long userId, Long workId);

    /**
     * 즐겨찾기 탭 (02 §10-2) — 그 계정 · 그 구분 · 숨김 아닌 곡.
     * 정렬은 <b>{@code created_at DESC, id DESC}</b> 다. id 를 2순위로 두는 이유: 같은 밀리초에 둘을 넣어도
     * 순서가 확정된다(테스트가 순서를 단언할 수 있어야 한다).
     *
     * <p><b>한 페이지의 곡만</b> 돌려준다 — {@code Page} 가 아니다. 총 개수는 {@link #countVisible} 하나가
     * 말하고({@code counts}), {@code MyLibraryQueryService} 가 그 값으로 페이지를 감싼다(03 §27).
     * 여기에 {@code countQuery} 를 붙이면 같은 수를 세는 곳이 둘이 돼, 숨김·구분 조건을 한쪽만 고치는 날
     * 응답은 200 인 채 {@code counts} 와 {@code totalElements} 만 조용히 갈린다(02 §10-2 는 "항상 같다" 가 계약).
     */
    @Query("select w from WorkFavoriteEntity f join f.work w"
            + " where f.user.id = :userId and w.hidden = false and w.section = :section"
            + " order by f.createdAt desc, f.id desc")
    List<WorkEntity> findFavoriteWorks(@Param("userId") Long userId,
                                       @Param("section") Section section,
                                       Pageable pageable);

    /** 탭 머리 숫자 — 숨김 제외 전체 수이고 page·size 와 무관하다(02 §10-2 {@code counts}). */
    @Query("select count(f) from WorkFavoriteEntity f join f.work w"
            + " where f.user.id = :userId and w.hidden = false and w.section = :section")
    long countVisible(@Param("userId") Long userId, @Param("section") Section section);

    /** 곡 삭제 (01_ERD §7) — 곡이 없으면 선반에 남길 것도 없다. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from WorkFavoriteEntity f where f.work.id = :workId")
    void deleteByWorkId(@Param("workId") Long workId);
}
