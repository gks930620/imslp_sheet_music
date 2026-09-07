package com.test.test.sheetmusic.work;

import com.test.test.common.exception.BusinessRuleException;
import java.util.ArrayList;
import java.util.List;

/** 곡 난이도 (01_ERD §4). NULL = 미정. */
public enum Level {
    BEGINNER, ELEMENTARY, INTERMEDIATE, ADVANCED;

    /**
     * 요청 파라미터 1개를 난이도로 읽는다 (대소문자 무시).
     * @return 비었으면 null(미정)
     * @throws BusinessRuleException 정의에 없는 값일 때
     */
    public static Level parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.trim();
        for (Level level : values()) {
            if (level.name().equalsIgnoreCase(trimmed)) {
                return level;
            }
        }
        throw new BusinessRuleException("난이도 값이 올바르지 않아요: " + raw);
    }

    /**
     * 콤마로 이어 붙인 난이도 목록을 읽는다 (예: {@code "BEGINNER,ADVANCED"}). 빈 항목은 건너뛴다.
     * @return 비었으면 빈 목록(필터 없음)
     */
    public static List<Level> parseCsv(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        List<Level> levels = new ArrayList<>();
        for (String part : raw.split(",")) {
            Level level = parse(part);
            if (level != null) {
                levels.add(level);
            }
        }
        return levels;
    }
}
