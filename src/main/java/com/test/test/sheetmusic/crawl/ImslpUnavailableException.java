package com.test.test.sheetmusic.crawl;

/**
 * IMSLP 무응답 — 타임아웃·5xx·429·봇 게이트(302) 반복 ({@link ImslpClient} 계약).
 * 워커는 항목을 FAILED / IMSLP_UNAVAILABLE 로 기록하고 연속 3항목이면 작업을 PAUSED 로 바꾼다 (02 §6-10).
 */
public class ImslpUnavailableException extends RuntimeException {

    public ImslpUnavailableException(String message) {
        super(message);
    }

    public ImslpUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
