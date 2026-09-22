package com.test.test.sheetmusic.edition;

/**
 * 추천 판본 지정 경고 (02 §5-6·§5-6-2, 2026-09-21 개정 — 기획 06 §3-2, 03 §30-3).
 *
 * <p>선언 순서가 곧 응답의 고정 순서다. 지정을 <b>막지 않는다</b> — 경고를 보고도 지정하면 그건 사람의 결정이고,
 * 그 결과는 사용자 화면(검색 한 줄·곡 상세·파일명 접미사)에 그대로 드러난다.
 *
 * <p>2026-09-21 — {@code WORK_BECOMES_CLOSED}·{@code HAS_DOWNLOAD_HISTORY}·{@code PARTS} 3종을 추가했다.
 * <b>기존 3종의 상대 순서는 바뀌지 않는다</b>({@code NOT_DOWNLOADABLE → ARRANGEMENT → PARTIAL_SCOPE}) —
 * {@code PARTS} 가 {@code ARRANGEMENT} 앞에 끼지만 둘은 {@code kind} 가 하나뿐이라 동시에 성립할 수 없다.
 */
public enum RecommendWarning {

    /** 지금 이 곡이 READY 인데 지정할 판본이 FREE 가 아니다 — 바꾸면 이 곡의 다운로드가 닫힌다. */
    WORK_BECOMES_CLOSED,

    /** 이 곡에 다운로드 기록이 있다 — 이미 받아 간 사람이 있다. */
    HAS_DOWNLOAD_HISTORY,

    /** 판정이 FREE 가 아니라 사용자에게 다운로드가 열리지 않는다. */
    NOT_DOWNLOADABLE,

    /** 한 악기 파트만 담은 판본이다. */
    PARTS,

    /** 편곡 판본이다 — 사용자가 원곡 악보를 기대하고 받을 수 있다. */
    ARRANGEMENT,

    /** 특정 악장만 들어 있다 — 곡 전체가 아니다. */
    PARTIAL_SCOPE
}
