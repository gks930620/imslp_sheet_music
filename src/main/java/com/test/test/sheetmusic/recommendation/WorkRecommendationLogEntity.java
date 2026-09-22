package com.test.test.sheetmusic.recommendation;

import com.test.test.sheetmusic.edition.EditionEntity;
import com.test.test.sheetmusic.edition.EditionKind;
import com.test.test.sheetmusic.edition.EditionScope;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 추천이 정해진 순간의 기록 (01_ERD §3-13, 2026-09-21 신설, 기획 06 §1·§5).
 *
 * <p>"고른 이유" 와 "바뀐 이력" 은 같은 표다(03 §30-1) — 맨 위 한 줄이 지금의 "고른 이유" 이고,
 * 쌓인 전체가 "바뀐 이력" 이다. <b>이 표는 append-only 다</b> — 곡 삭제(§7)·판본 삭제의 참조 끊기를 빼면
 * 어떤 경로도 기존 행을 고치거나 지우지 않는다.
 *
 * <p><b>판본 표기를 양쪽(edition/previous) 다 스냅샷한다</b>(03 §30-2) — 출판사·편집자·연도는 관리자가 고칠 수
 * 있는 값이라 지금 판본에서 다시 읽으면 그때의 판단이 아니라 오늘의 값이 된다. 판본이 삭제돼도 이 값들은 남는다.
 *
 * <p>{@code created_at}/{@code updated_at} 을 두지 않는다 — 이 표는 사건 기록이라 행 생성 = 사건 발생이고,
 * {@code decided_at} 이 그 시각이자 정렬 키다. 갱신 경로가 없으므로 {@code updated_at} 은 뜻이 없다.
 */
@Entity
@Table(name = "work_recommendation_log",
        indexes = @Index(name = "idx_work_recommendation_log_work", columnList = "work_id, decided_at, id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorkRecommendationLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "work_id", nullable = false)
    private Long workId;

    @Column(name = "decided_at", nullable = false)
    private Instant decidedAt;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "source", length = 30, nullable = false)
    private RecommendationSource source;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "action", length = 30, nullable = false)
    private RecommendationAction action;

    /** {@code source = ADMIN} 일 때만. 토큰 주체로만 채운다(컨벤션 §4-1) — 요청 본문의 사용자 id 를 쓰지 않는다. */
    @Column(name = "decided_by_user_id")
    private Long decidedByUserId;

    /** 그때의 닉네임 스냅샷(로그인 아이디는 흘리지 않는다). 비어 있었으면 NULL. */
    @Column(name = "decided_by_nickname", length = 100)
    private String decidedByNickname;

    /** 이 순간 추천이 된 판본. {@code action = CLEARED} 면 NULL. 판본이 지워지면 NULL(§7). */
    @Column(name = "edition_id")
    private Long editionId;

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

    @Column(name = "edition_publisher", length = 300)
    private String editionPublisher;

    @Column(name = "edition_editor", length = 200)
    private String editionEditor;

    @Column(name = "edition_publish_year")
    private Integer editionPublishYear;

    /** 직전 추천. NULL = 처음 지정. 판본이 지워지면 NULL(§7). */
    @Column(name = "previous_edition_id")
    private Long previousEditionId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "previous_kind", length = 30)
    private EditionKind previousKind;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "previous_scope", length = 30)
    private EditionScope previousScope;

    @Column(name = "previous_movement_number")
    private Integer previousMovementNumber;

    @Column(name = "previous_publisher", length = 300)
    private String previousPublisher;

    @Column(name = "previous_editor", length = 200)
    private String previousEditor;

    @Column(name = "previous_publish_year")
    private Integer previousPublishYear;

    /** {@code source = AUTO} 일 때만. */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "auto_rule", length = 30)
    private RecommendationAutoRule autoRule;

    /** 그때의 IMSLP 다운로드 수. NULL = "다운로드 수가 적혀 있지 않은 판본". */
    @Column(name = "auto_imslp_download_count")
    private Integer autoImslpDownloadCount;

    /** 그때의 후보 수. */
    @Column(name = "auto_candidate_count")
    private Integer autoCandidateCount;

    /** 그때의 순위. */
    @Column(name = "auto_rank")
    private Integer autoRank;

    /** {@code source = ADMIN} + {@code action = ASSIGNED} 일 때만. */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "reason", length = 30)
    private RecommendationReason reason;

    /** {@code reason = OTHER} 면 필수(검증은 API 계층 — 02 §5-6). */
    @Column(name = "note", length = 300)
    private String note;

    /** {@code action = CLEARED} 일 때만. */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "cleared_reason", length = 30)
    private RecommendationClearedReason clearedReason;

    private WorkRecommendationLogEntity(Long workId, Instant decidedAt, RecommendationSource source,
                                        RecommendationAction action, Long decidedByUserId,
                                        String decidedByNickname, EditionEntity edition, EditionEntity previousEdition,
                                        RecommendationAutoRule autoRule, Integer autoCandidateCount, Integer autoRank,
                                        RecommendationReason reason, String note,
                                        RecommendationClearedReason clearedReason) {
        this.workId = workId;
        this.decidedAt = decidedAt;
        this.source = source;
        this.action = action;
        this.decidedByUserId = decidedByUserId;
        this.decidedByNickname = blankToNull(decidedByNickname);
        applyEditionSnapshot(edition);
        applyPreviousSnapshot(previousEdition);
        this.autoRule = autoRule;
        // source = AUTO 일 때만 값이 있다 — autoRule 유무로 구분한다(ADMIN/CLEARED 행은 전부 NULL 이어야 한다).
        this.autoImslpDownloadCount = autoRule == null || edition == null ? null : edition.getImslpDownloadCount();
        this.autoCandidateCount = autoCandidateCount;
        this.autoRank = autoRank;
        this.reason = reason;
        this.note = note;
        this.clearedReason = clearedReason;
    }

    /** 관리자 지정 (02 §5-6). {@code previousEdition} 이 없으면(첫 지정) 전부 NULL 이 된다. */
    public static WorkRecommendationLogEntity adminAssigned(Long workId, Instant decidedAt, Long decidedByUserId,
                                                             String decidedByNickname, EditionEntity edition,
                                                             EditionEntity previousEdition,
                                                             RecommendationReason reason, String note) {
        return new WorkRecommendationLogEntity(workId, decidedAt, RecommendationSource.ADMIN,
                RecommendationAction.ASSIGNED, decidedByUserId, decidedByNickname, edition, previousEdition,
                null, null, null, reason, note, null);
    }

    /**
     * 자동 지정 (02 §5-11). 추천이 없는 곡에만 걸리므로 <b>항상 첫 지정</b>이다(previous 전부 NULL).
     * {@code auto_imslp_download_count} 는 지정한 그 판본의 그 순간 값을 그대로 박는다(생성자에서 스냅샷).
     */
    public static WorkRecommendationLogEntity autoAssigned(Long workId, Instant decidedAt, EditionEntity edition,
                                                            int candidateCount, int rank) {
        return new WorkRecommendationLogEntity(workId, decidedAt, RecommendationSource.AUTO,
                RecommendationAction.ASSIGNED, null, null, edition, null,
                RecommendationAutoRule.MOST_IMSLP_DOWNLOADS, candidateCount, rank, null, null, null);
    }

    /** 추천이 빠짐 (02 §5-3·§5-5) — 지워지거나 파일이 떨어진 판본을 {@code previousEdition} 자리에 스냅샷한다. */
    public static WorkRecommendationLogEntity cleared(Long workId, Instant decidedAt, Long decidedByUserId,
                                                       String decidedByNickname, EditionEntity clearedEdition,
                                                       RecommendationClearedReason clearedReason) {
        return new WorkRecommendationLogEntity(workId, decidedAt, RecommendationSource.ADMIN,
                RecommendationAction.CLEARED, decidedByUserId, decidedByNickname, null, clearedEdition,
                null, null, null, null, null, clearedReason);
    }

    private void applyEditionSnapshot(EditionEntity edition) {
        this.editionId = edition == null ? null : edition.getId();
        this.editionKind = edition == null ? null : edition.getKind();
        this.editionScope = edition == null ? null : edition.getScope();
        this.editionMovementNumber = edition == null ? null : edition.getMovementNumber();
        this.editionPublisher = edition == null ? null : edition.getPublisher();
        this.editionEditor = edition == null ? null : edition.getEditor();
        this.editionPublishYear = edition == null ? null : edition.getPublishYear();
    }

    private void applyPreviousSnapshot(EditionEntity edition) {
        this.previousEditionId = edition == null ? null : edition.getId();
        this.previousKind = edition == null ? null : edition.getKind();
        this.previousScope = edition == null ? null : edition.getScope();
        this.previousMovementNumber = edition == null ? null : edition.getMovementNumber();
        this.previousPublisher = edition == null ? null : edition.getPublisher();
        this.previousEditor = edition == null ? null : edition.getEditor();
        this.previousPublishYear = edition == null ? null : edition.getPublishYear();
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }
}
