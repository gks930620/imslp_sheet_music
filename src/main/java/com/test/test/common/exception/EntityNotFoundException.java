package com.test.test.common.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;

/**
 * 엔티티를 찾을 수 없을 때 발생하는 예외 — HTTP 404 {@code NOT_FOUND}.
 *
 * <p>메시지는 화면에 그대로 보인다(02 §0-2). 그래서 형식은 <b>{@code "{리소스}{을|를} 찾을 수 없어요"}</b> 이고
 * <b>내부 식별자를 붙이지 않는다</b> — 사용자에게 의미가 없고 내부 id 를 흘린다. 식별자는 로그에만 남긴다.
 */
@Slf4j
public class EntityNotFoundException extends BusinessException {

    /** 한글 음절 영역 시작(가). 받침 계산의 기준점. */
    private static final char HANGUL_FIRST = 0xAC00;

    /** 한글 음절 영역 끝(힣). */
    private static final char HANGUL_LAST = 0xD7A3;

    /** 한 초성·중성 조합당 종성(받침) 가짓수 — 나머지가 0 이면 받침이 없다. */
    private static final int JONGSUNG_COUNT = 28;

    public EntityNotFoundException(String message) {
        super(message, HttpStatus.NOT_FOUND, "NOT_FOUND");
    }

    public static EntityNotFoundException of(String entityName, Long id) {
        return of(entityName, String.valueOf(id));
    }

    public static EntityNotFoundException of(String entityName, String identifier) {
        log.warn("Entity not found: {} (identifier={})", entityName, identifier);
        return new EntityNotFoundException(entityName + objectJosa(entityName) + " 찾을 수 없어요");
    }

    /**
     * 목적격 조사를 받침으로 고른다 — {@code 곡을 / 판본을}, {@code 작곡가를 / 사용자를}.
     * 리소스명이 늘어도 {@code "작곡가을(를)"} 처럼 문구가 깨지지 않는다.
     */
    private static String objectJosa(String noun) {
        if (noun == null || noun.isEmpty()) {
            return "를";
        }
        char last = noun.charAt(noun.length() - 1);
        if (last < HANGUL_FIRST || last > HANGUL_LAST) {
            return "를"; // 한글이 아니면(영문·숫자) 받침을 계산할 수 없다
        }
        return (last - HANGUL_FIRST) % JONGSUNG_COUNT != 0 ? "을" : "를";
    }
}
