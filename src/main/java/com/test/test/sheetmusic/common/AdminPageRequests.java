package com.test.test.sheetmusic.common;

import org.springframework.data.domain.PageRequest;

/**
 * 관리 목록 API 의 페이지 요청 정규화 (02 §4-2·§4-6·§5-8) — 관리 화면 세 목록이 같은 규칙을 쓰도록 한 곳에 모은다.
 *
 * <p>{@code size} 는 선택이다. 보내지 않거나 1 미만이면 기본 20, 200 을 넘으면 200 으로 자른다.
 * (상한이 없으면 한 번의 요청으로 테이블 전체를 끌어올 수 있고, 0 이하는 {@link PageRequest} 가 예외를 던진다.)
 * 관리 화면의 작곡가 select 는 {@code ?size=200} 으로 선택지를 한 번에 채운다.
 */
public final class AdminPageRequests {

    /** {@code size} 를 보내지 않았을 때. */
    public static final int DEFAULT_SIZE = 20;

    /** 한 번에 가져갈 수 있는 최대 행 수. */
    public static final int MAX_SIZE = 200;

    private AdminPageRequests() {
    }

    public static PageRequest of(int page, Integer size) {
        return PageRequest.of(Math.max(page, 0), normalizeSize(size));
    }

    private static int normalizeSize(Integer size) {
        if (size == null || size < 1) {
            return DEFAULT_SIZE;
        }
        return Math.min(size, MAX_SIZE);
    }
}
