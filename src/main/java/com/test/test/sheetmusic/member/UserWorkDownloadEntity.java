package com.test.test.sheetmusic.member;

import com.test.test.jwt.entity.UserEntity;
import com.test.test.sheetmusic.edition.EditionEntity;
import com.test.test.sheetmusic.edition.EditionKind;
import com.test.test.sheetmusic.edition.EditionScope;
import com.test.test.sheetmusic.work.WorkEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 받은 악보 (01_ERD §3-12) — <b>곡 단위 한 줄</b>이다(기획 05 §3-2). 같은 곡을 몇 번 받아도 행은 늘지 않고
 * {@code last_downloaded_at} 과 스냅샷만 마지막 다운로드의 값으로 덮인다.
 *
 * <p>{@code download_log}(집계 원장)는 한 글자도 바꾸지 않는다(03 §24) — 비로그인 다운로드는 계속 거기 쌓이고
 * <b>이 표에는 행을 만들지 않는다</b>(기획 05 §3-1).
 *
 * <p><b>판본 설명 5개를 스냅샷하는 이유</b>: 화면은 "다시 받기" 를 누르기 전에 무엇을 받는지 보여야 하고,
 * 판본이 <b>삭제된 뒤에도</b> 그 줄을 남겨야 한다(화면정의 09 §6 S4). {@code last_edition_id} 가 NULL 이 되면
 * "못 준다" 는 판정은 되지만 "무엇을" 못 주는지는 말할 수 없다. 스냅샷은 그 줄에 쓰이는 값만이다 —
 * 출판사·편집자·저작권 표기는 담지 않는다(담으면 "지금 판본" 과 어긋난 옛 값을 우리가 보관하게 된다).
 */
@Entity
@Table(name = "user_work_download",
        uniqueConstraints = @UniqueConstraint(name = "uk_user_work_download",
                columnNames = {"user_id", "work_id"}),
        indexes = {
                @Index(name = "idx_user_work_download_user_time", columnList = "user_id, last_downloaded_at"),
                @Index(name = "idx_user_work_download_work", columnList = "work_id")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserWorkDownloadEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 토큰 주체. 비로그인 다운로드는 이 표에 오지 않는다(기획 05 §3-1). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "work_id", nullable = false)
    private WorkEntity work;

    /** 정렬 키이자 화면의 "9월 12일 받음". 같은 곡을 다시 받으면 갱신된다. */
    @Column(name = "last_downloaded_at", nullable = false)
    private Instant lastDownloadedAt;

    /** 가장 최근에 받은 판본. <b>판본이 지워지면 NULL</b>(01_ERD §7) — 스냅샷 5개는 그대로 남는다. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "last_edition_id")
    private EditionEntity lastEdition;

    /**
     * 받은 순간의 판본 설명(스냅샷).
     * {@code @JdbcTypeCode(VARCHAR)} 가 없으면 네이티브 {@code enum(...)} 컬럼이 되살아나
     * 다음에 {@link EditionKind} 상수를 더할 때 기동이 깨진다(01_ERD §9-1).
     */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "edition_kind", length = 30)
    private EditionKind editionKind;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "edition_scope", length = 30)
    private EditionScope editionScope;

    @Column(name = "edition_movement_number")
    private Integer editionMovementNumber;

    @Column(name = "edition_page_count")
    private Integer editionPageCount;

    @Column(name = "edition_file_size")
    private Long editionFileSize;

    /** 그 사람이 이 곡을 <b>처음</b> 받은 시각(표시에는 쓰지 않는다). */
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /**
     * {@code created_at} 은 <b>여기서만</b> 정해진다 — 01_ERD §1 "생성/수정 시각"({@code @PrePersist})의
     * <b>유일한 예외</b>다(03 §26-4). 넣기가 네이티브 한 문장이라 {@code @PrePersist} 가 돌지 않으므로,
     * 규칙을 두 곳에 두는 대신 생성자 한 곳에 둔다 — 누가 {@code save()} 로 되돌아가도 같은 값이 들어간다.
     * 값은 <b>그 다운로드 시각</b>이고 다시 받아도 갱신되지 않는다.
     */
    public UserWorkDownloadEntity(UserEntity user, WorkEntity work, EditionEntity edition,
                                  Long fileSize, Instant downloadedAt) {
        this.user = user;
        this.work = work;
        this.createdAt = downloadedAt;
        record(edition, fileSize, downloadedAt);
    }

    /** 다운로드 1건 — 마지막 판본·스냅샷·받은 시각을 그 다운로드의 값으로 덮는다(01_ERD §3-12). */
    public void record(EditionEntity edition, Long fileSize, Instant downloadedAt) {
        this.lastEdition = edition;
        this.editionKind = edition == null ? null : edition.getKind();
        this.editionScope = edition == null ? null : edition.getScope();
        this.editionMovementNumber = edition == null ? null : edition.getMovementNumber();
        this.editionPageCount = edition == null ? null : edition.getPageCount();
        this.editionFileSize = fileSize;
        this.lastDownloadedAt = downloadedAt;
    }

    /** 그때 받은 판본이 아직 살아 있는가 — 살아 있으면 화면은 스냅샷이 아니라 지금 값을 보인다(02 §10-3). */
    public boolean hasLiveEdition() {
        return this.lastEdition != null;
    }

    /** 스냅샷이 한 줄이라도 말할 수 있는가 — 아무것도 없으면 화면이 "받은 판본" 줄을 생략한다. */
    public boolean hasSnapshot() {
        return this.editionKind != null || this.editionScope != null || this.editionMovementNumber != null
                || this.editionPageCount != null || this.editionFileSize != null;
    }
}
