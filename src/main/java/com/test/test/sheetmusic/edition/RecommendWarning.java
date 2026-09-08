package com.test.test.sheetmusic.edition;

/**
 * 추천 판본 지정 경고 (02 §5-6, 2026-09-08 개정 — 기획 §11-2 ①).
 *
 * <p>선언 순서가 곧 응답의 고정 순서다. 지정을 <b>막지 않는다</b> — 경고를 보고도 지정하면 그건 사람의 결정이고,
 * 그 결과는 사용자 화면(검색 한 줄·곡 상세·파일명 접미사)에 그대로 드러난다.
 */
public enum RecommendWarning {

    /** 판정이 FREE 가 아니라 사용자에게 다운로드가 열리지 않는다. */
    NOT_DOWNLOADABLE,

    /** 편곡 판본이다 — 사용자가 원곡 악보를 기대하고 받을 수 있다. */
    ARRANGEMENT,

    /** 특정 악장만 들어 있다 — 곡 전체가 아니다. */
    PARTIAL_SCOPE
}
