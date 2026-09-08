package com.test.test.sheetmusic.work;

/**
 * 검색 항목 "범위 한 줄" 의 코드 (02 §2-2-1). 선언 순서가 곧 응답의 고정 순서다.
 * 문구는 화면이 만든다 — 서버가 완성 문장을 내려보내면 한 글자 고치는 데 배포가 필요하다.
 */
public enum ScopeNoteCode {

    /** 곡에 수록곡 안내(collection_guide)가 있다 = 묶음 악보. */
    COLLECTION,

    /** 추천 판본이 편곡이다. */
    ARRANGEMENT,

    /** 추천 판본이 특정 악장만 담고 있다. */
    MOVEMENT_ONLY
}
