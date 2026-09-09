package com.test.test.sheetmusic.work.repository;

import com.test.test.sheetmusic.work.WorkEntity;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** 곡 동적 조회 (03 §7 — QueryDSL + 정규화 컬럼 LIKE). */
public interface WorkRepositoryCustom {

    Page<WorkEntity> search(WorkSearchCondition condition, Pageable pageable);

    long count(WorkSearchCondition condition);

    /**
     * 홈 "지금 바로 받을 수 있는 인기곡" (02 §3-2, 2026-09-08 전면 개정).
     * 자격 곡(READY + 한국어 제목)만 오르고, 모자란 칸만 준비 중 곡으로 채운다. 자격 곡이 0개면 빈 목록.
     */
    List<WorkEntity> findPopular(int limit);

    /** 관리 홈 {@code needsWorkWorks} (02 §4-1) — 목록 필터·곡 상세와 같은 {@code WorkNeedsWork} 규칙. */
    long countNeedsWork();

    /**
     * 관리 홈 {@code needsRecommendationReviewWorks} (02 §4-1) — 곡 목록 필터
     * {@code status=NEEDS_RECOMMENDATION_REVIEW} 와 같은 {@code WorkRecommendationReview} 규칙(숨김 제외).
     */
    long countNeedsRecommendationReview();
}
