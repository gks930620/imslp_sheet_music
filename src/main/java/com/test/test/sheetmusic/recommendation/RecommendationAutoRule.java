package com.test.test.sheetmusic.recommendation;

/**
 * 자동 추천 지정 규칙 (01_ERD §3-13, 02 §5-11, 2026-09-21 신설). {@code work_recommendation_log.auto_rule}.
 *
 * <p><b>규칙이 바뀌면 상수를 더하고 옛 줄은 옛 상수를 유지한다</b>(기획 06 §4-2) — 그게 그때의 판단이다.
 * 기존 상수의 뜻을 바꾸지 않는다.
 */
public enum RecommendationAutoRule {

    /** 전체 악보 · 전곡 · 파일 있는 판본 중 IMSLP 다운로드가 가장 많은 것. */
    MOST_IMSLP_DOWNLOADS
}
