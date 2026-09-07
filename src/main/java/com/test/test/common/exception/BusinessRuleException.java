package com.test.test.common.exception;

import org.springframework.http.HttpStatus;

/**
 * 비즈니스 규칙 위반 시 발생하는 예외
 * HTTP 400 Bad Request
 */
public class BusinessRuleException extends BusinessException {

    public BusinessRuleException(String message) {
        super(message, HttpStatus.BAD_REQUEST, "BUSINESS_RULE_VIOLATION");
    }
}

