package com.test.test.sheetmusic.recommendation;

/**
 * 관리자가 추천을 지정한 사유 (01_ERD §3-13, 02 §5-6, 2026-09-21 신설).
 *
 * <p><b>선언 순서가 곧 화면 나열 순서다</b>(화면정의 06 A-3 ③). 문구는 서버가 갖지 않는다 — 화면이 문구를 갖는다.
 * {@code source = ADMIN} + {@code action = ASSIGNED} 일 때만 값이 있다. {@code OTHER} 면 {@code note} 가 필수다.
 */
public enum RecommendationReason {

    /** 앞 추천이 이 곡의 악보가 아니었어요. */
    NOT_THIS_WORK,

    /** 이 판본이 더 읽기 좋아요. */
    BETTER_READABILITY,

    /** 이 판본의 편집·운지가 배우는 사람에게 맞아요. */
    BETTER_FOR_LEARNERS,

    /** 앞 추천은 지금 받을 수 없어요. */
    PREVIOUS_UNAVAILABLE,

    /** 이 판본이 곡 전체를 담고 있어요. */
    COVERS_WHOLE_WORK,

    /** 기타 — 직접 적기(메모 필수). */
    OTHER
}
