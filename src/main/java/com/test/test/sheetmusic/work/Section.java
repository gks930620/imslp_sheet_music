package com.test.test.sheetmusic.work;

import com.test.test.common.exception.BusinessRuleException;

/**
 * 악기 구분 (01_ERD §3-3, 02 §0-7). 곡은 정확히 하나의 구분에 속한다.
 *
 * <p>쿼리 파라미터는 대소문자를 무시하고, 생략하면 {@link #PIANO} 다 — 구분이 없던 시절의 링크가 그대로 동작한다(기획 04 §3-5).
 * 정의되지 않은 값은 400 이다. 검색 기준 {@link SearchIn} 의 관용 처리와 <b>일부러 다르다</b>:
 * 구분은 "어느 세계를 보고 있는가" 라서 조용히 다른 세계를 보여주면 사용자가 잘못된 결과를 옳은 것으로 읽는다.
 */
public enum Section {
    PIANO, VIOLIN, ORCHESTRA;

    /**
     * 쿼리 문자열 → 구분. 비었으면 PIANO, 대소문자 무시.
     * @throws BusinessRuleException 정의에 없는 값일 때 (400)
     */
    public static Section from(String raw) {
        if (raw == null || raw.isBlank()) {
            return PIANO;
        }
        String trimmed = raw.trim();
        for (Section section : values()) {
            if (section.name().equalsIgnoreCase(trimmed)) {
                return section;
            }
        }
        throw new BusinessRuleException("악기 구분 값이 올바르지 않아요: " + raw);
    }
}
