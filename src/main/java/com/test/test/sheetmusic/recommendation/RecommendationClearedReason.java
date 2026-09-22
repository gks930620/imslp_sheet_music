package com.test.test.sheetmusic.recommendation;

/**
 * 추천이 빠진 사유 (01_ERD §3-13, 2026-09-21 신설). {@code work_recommendation_log.cleared_reason}.
 * {@code action = CLEARED} 일 때만 값이 있다 — 삭제에는 사람이 고르는 사유를 묻지 않는다(화면정의 06 A-2).
 */
public enum RecommendationClearedReason {

    /** 추천 판본을 삭제(02 §5-5). */
    EDITION_DELETED,

    /** 추천 판본에서 파일을 뗌(02 §5-3). */
    EDITION_FILE_REMOVED
}
