package com.test.test.common.exception;

import org.springframework.http.HttpStatus;

/**
 * 파일 메타(files 행)는 있는데 저장된 바이트를 읽을 수 없을 때 (02_API_명세서 §0-2). HTTP 503.
 */
public class FileUnavailableException extends BusinessException {

    public FileUnavailableException(String message) {
        super(message, HttpStatus.SERVICE_UNAVAILABLE, "FILE_UNAVAILABLE");
    }
}
