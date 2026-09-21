package com.test.test.sheetmusic.member;

/**
 * 받은 악보 항목의 "다시 받기" 3상태 (02 §10-3, 화면정의 09 §1-2-2).
 *
 * <p>{@link #RECOMMENDATION_CHANGED} 에는 <b>추천이 아예 없어진 곡</b>도 든다 — 그때 받은 판본은 여전히 줄 수 있으니
 * 화면이 하는 일이 같기 때문이다(버튼 + 한 줄 + 곡 보기). 그래서 상태를 넷으로 쪼개지 않는다.
 */
public enum RedownloadState {

    /** ① 그때 받은 판본이 지금 추천 그대로이고 받을 수 있다. */
    AVAILABLE,

    /** ② 그때 받은 판본은 받을 수 있는데 지금 추천이 다르거나 없다. */
    RECOMMENDATION_CHANGED,

    /** ③ 그때 받은 판본을 지금은 줄 수 없다(삭제·파일 없음·이용 제한). */
    UNAVAILABLE
}
