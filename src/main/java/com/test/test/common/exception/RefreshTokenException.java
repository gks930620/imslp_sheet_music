package com.test.test.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Refresh 토큰 관련 예외 (HTTP 401)
 * - 기존 RefreshController가 내려주던 errorCode 계약(TOKEN_DISCARDED/TOKEN_EXPIRED/TOKEN_REQUIRED)을 유지한다.
 */
public class RefreshTokenException extends BusinessException {

    public RefreshTokenException(String message, String errorCode) {
        super(message, HttpStatus.UNAUTHORIZED, errorCode);
    }
}
