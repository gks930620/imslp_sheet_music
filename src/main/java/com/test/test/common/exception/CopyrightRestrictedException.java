package com.test.test.common.exception;

import org.springframework.http.HttpStatus;

/**
 * 저작권 판정 때문에 다운로드할 수 없을 때 (02_API_명세서 §0-2). HTTP 403.
 */
public class CopyrightRestrictedException extends BusinessException {

    public CopyrightRestrictedException(String message) {
        super(message, HttpStatus.FORBIDDEN, "COPYRIGHT_RESTRICTED");
    }
}
