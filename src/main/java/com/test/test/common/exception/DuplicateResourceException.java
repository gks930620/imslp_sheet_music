package com.test.test.common.exception;

import org.springframework.http.HttpStatus;

/**
 * 이미 처리된 리소스에 대한 작업 시 발생하는 예외
 * HTTP 409 Conflict
 */
public class DuplicateResourceException extends BusinessException {

    public DuplicateResourceException(String message) {
        super(message, HttpStatus.CONFLICT, "DUPLICATE_RESOURCE");
    }

    public static DuplicateResourceException alreadyDeleted(String entityName) {
        return new DuplicateResourceException("이미 삭제된 " + entityName + "입니다.");
    }

    public static DuplicateResourceException alreadyExists(String message) {
        return new DuplicateResourceException(message);
    }
}

