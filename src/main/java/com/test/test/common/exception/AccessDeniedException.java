package com.test.test.common.exception;

import org.springframework.http.HttpStatus;

/**
 * 권한이 없을 때 발생하는 예외 (본인만 수정/삭제 가능한 경우)
 * HTTP 403 Forbidden
 */
public class AccessDeniedException extends BusinessException {

    public AccessDeniedException(String message) {
        super(message, HttpStatus.FORBIDDEN, "ACCESS_DENIED");
    }

    public static AccessDeniedException forUpdate(String entityName) {
        return new AccessDeniedException("본인의 " + entityName + "만 수정할 수 있습니다.");
    }

    public static AccessDeniedException forDelete(String entityName) {
        return new AccessDeniedException("본인의 " + entityName + "만 삭제할 수 있습니다.");
    }

    public static AccessDeniedException forFile() {
        return new AccessDeniedException("본인이 작성한 리소스의 파일만 업로드/삭제할 수 있습니다.");
    }
}

