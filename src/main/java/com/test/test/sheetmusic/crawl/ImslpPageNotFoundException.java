package com.test.test.sheetmusic.crawl;

/**
 * IMSLP 가 404 를 준 경우 ({@link ImslpClient} 계약). 워커는 항목을 FAILED / PAGE_NOT_FOUND 로 기록하고 다음 항목으로 넘어간다.
 * 사용자에게 노출되는 HTTP 예외가 아니므로 {@code BusinessException} 계층에 넣지 않는다.
 */
public class ImslpPageNotFoundException extends RuntimeException {

    public ImslpPageNotFoundException(String url) {
        super("IMSLP 페이지 없음: " + url);
    }
}
