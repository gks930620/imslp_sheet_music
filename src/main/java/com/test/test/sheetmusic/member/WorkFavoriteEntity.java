package com.test.test.sheetmusic.member;

import com.test.test.jwt.entity.UserEntity;
import com.test.test.sheetmusic.work.WorkEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 즐겨찾기 (01_ERD §3-11) — <b>곡 단위</b>이고 판본을 가리지 않는다(기획 05 §1-4).
 *
 * <p>{@code uk_work_favorite(user_id, work_id)} 가 곧 멱등의 근거다(02 §10-1): 이미 있으면 아무것도 하지 않고
 * <b>{@code created_at} 도 갱신하지 않는다</b>. 다시 누른 것은 "새로 넣은 것" 이 아니라서, 순서가 흔들리면
 * 즐겨찾기 탭의 "최근에 넣은 순" 이 거짓말이 된다.
 *
 * <p>숨긴 곡의 행은 <b>지우지 않는다</b> — 목록·숫자에서만 빠지고 숨김이 풀리면 그 자리로 돌아온다.
 * 곡이 삭제될 때만 함께 사라진다(01_ERD §7).
 */
@Entity
@Table(name = "work_favorite",
        uniqueConstraints = @UniqueConstraint(name = "uk_work_favorite", columnNames = {"user_id", "work_id"}),
        indexes = {
                @Index(name = "idx_work_favorite_user_created", columnList = "user_id, created_at"),
                @Index(name = "idx_work_favorite_work", columnList = "work_id")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorkFavoriteEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 계정. <b>토큰 주체로만 채운다</b> — 클라이언트가 준 userId 를 쓰지 않는다(컨벤션 §4-1, 02 §10-0). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "work_id", nullable = false)
    private WorkEntity work;

    /** 정렬 키 — 즐겨찾기 탭은 "최근에 넣은 순"(02 §10-2). 다시 켜도 갱신되지 않는다. */
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public WorkFavoriteEntity(UserEntity user, WorkEntity work) {
        this.user = user;
        this.work = work;
    }

    @PrePersist
    void prePersist() {
        this.createdAt = Instant.now();
    }
}
