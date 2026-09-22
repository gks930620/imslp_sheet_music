package com.test.test.sheetmusic.recommendation;

/** 그 순간 추천에 일어난 일 (01_ERD §3-13, 2026-09-21 신설). {@code work_recommendation_log.action}. */
public enum RecommendationAction {

    /** 추천이 정해짐(지정). */
    ASSIGNED,

    /** 추천이 빠짐(해제) — 판본 삭제(§5-5) · 파일 제거(§5-3)로만 일어난다. */
    CLEARED
}
