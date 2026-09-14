package com.test.test.sheetmusic.work;

/**
 * 검색 기준 (02 §3-1 "검색 기준 in", 기획 04 §4). 규칙 2번의 OR 묶음만 좁힌다 — 하는 일은 "어느 칸에서 찾는가" 하나뿐이다.
 *
 * <p>알 수 없는 값은 오류가 아니라 {@link #ALL} 이다(기획 04 §4-5). 사용자는 주소를 손으로 편집하지 않고,
 * 링크가 조금 상했다고 결과를 통째로 안 주는 건 손해만 크다. {@code level}·{@code pages} 가 400 인 것과 다른 이유는
 * 그 둘은 결과를 좁히는 조건이라 조용히 무시하면 안 건 필터가 걸린 줄 알지만, 이 값은 가장 넓은 기본값으로
 * 떨어져 사용자가 잃는 것이 없기 때문이다. 응답의 {@code in} 은 이 관용 처리가 끝난 확정값이다.
 */
public enum SearchIn {
    ALL, TITLE, COMPOSER;

    /** 대소문자 무시. null·빈 값·알 수 없는 값은 전부 ALL — 예외를 던지지 않는다(200). */
    public static SearchIn from(String raw) {
        if (raw == null || raw.isBlank()) {
            return ALL;
        }
        String trimmed = raw.trim();
        for (SearchIn value : values()) {
            if (value.name().equalsIgnoreCase(trimmed)) {
                return value;
            }
        }
        return ALL;
    }

    /** 곡 칸(한국어 제목·원어 제목·곡 별칭·작품번호)을 찾는가 — 02 §3-1 표. */
    public boolean searchesTitleFields() {
        return this != COMPOSER;
    }

    /** 작곡가 칸(한글·원어·작곡가 별칭)을 찾는가 — 02 §3-1 표. */
    public boolean searchesComposerFields() {
        return this != TITLE;
    }
}
