package com.test.test.common.exception;

import org.springframework.http.HttpStatus;

/**
 * 업로드 상한 초과 (02_API_명세서 §0-2, 03 §5). HTTP 413.
 * 상한은 {@code app.edition.max-file-bytes} 이며 문구는 그 값에서 만든다.
 */
public class PayloadTooLargeException extends BusinessException {

    public PayloadTooLargeException(String message) {
        super(message, HttpStatus.PAYLOAD_TOO_LARGE, "PAYLOAD_TOO_LARGE");
    }

    /** {@code "{MB}MB 이하만 올릴 수 있어요"} */
    public static PayloadTooLargeException ofLimit(long maxBytes) {
        return new PayloadTooLargeException(limitMessage(maxBytes));
    }

    public static String limitMessage(long maxBytes) {
        long megabytes = Math.max(1L, maxBytes / (1024L * 1024L));
        return megabytes + "MB 이하만 올릴 수 있어요";
    }
}
