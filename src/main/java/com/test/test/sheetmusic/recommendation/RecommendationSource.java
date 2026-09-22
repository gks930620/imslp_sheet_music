package com.test.test.sheetmusic.recommendation;

/**
 * 추천을 정한 주체 (01_ERD §3-13, 02 §4-6·§4-7-2, 2026-09-21 신설).
 *
 * <p>{@code work_recommendation_log.source} — 화면 1단: {@code AUTO} 는 "자동", {@code ADMIN} 은 "{닉네임} 님".
 */
public enum RecommendationSource {

    /** 시스템이 규칙(§5-11)으로 지정. */
    AUTO,

    /** 사람이 손으로 지정(§5-6). */
    ADMIN
}
