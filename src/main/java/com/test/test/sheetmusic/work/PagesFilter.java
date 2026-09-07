package com.test.test.sheetmusic.work;

import com.test.test.common.exception.BusinessRuleException;

/** 추천 판본 쪽수 구간 필터 (02 §3-1). 쿼리 값은 LE10 / 11_20 / GE21. */
public enum PagesFilter {

    LE10(null, 10),
    RANGE_11_20(11, 20),
    GE21(21, null);

    private final Integer min;
    private final Integer max;

    PagesFilter(Integer min, Integer max) {
        this.min = min;
        this.max = max;
    }

    public Integer getMin() {
        return min;
    }

    public Integer getMax() {
        return max;
    }

    /** 쿼리 문자열 → enum. 정의되지 않은 값은 400. */
    public static PagesFilter from(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return switch (value.trim()) {
            case "LE10" -> LE10;
            case "11_20" -> RANGE_11_20;
            case "GE21" -> GE21;
            default -> throw new BusinessRuleException("쪽수 구간 값이 올바르지 않아요: " + value);
        };
    }
}
