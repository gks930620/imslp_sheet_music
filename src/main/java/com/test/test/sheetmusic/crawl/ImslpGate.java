package com.test.test.sheetmusic.crawl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * IMSLP 요청 간격 게이트 (03 §3).
 * robots {@code Crawl-delay: 2} 를 지키고, 파일 대기 페이지의 15초를 우회하지 않는다.
 * 테스트는 {@code app.imslp.request-interval-ms=0}, {@code app.imslp.file-wait-ms=0} 으로 즉시 통과시킨다.
 */
@Component
@Slf4j
public class ImslpGate {

    @Value("${app.imslp.request-interval-ms:2000}")
    private long requestIntervalMs;

    @Value("${app.imslp.file-wait-ms:15000}")
    private long fileWaitMs;

    private long lastRequestAt;

    /** 직전 요청 완료 시각 기준 최소 간격을 채운다. 커넥션은 항상 1개라 동기화만으로 충분하다. */
    public synchronized void awaitRequestSlot() {
        if (requestIntervalMs <= 0) {
            lastRequestAt = System.currentTimeMillis();
            return;
        }
        long wait = lastRequestAt + requestIntervalMs - System.currentTimeMillis();
        sleep(wait);
        lastRequestAt = System.currentTimeMillis();
    }

    /** 파일 대기 페이지의 카운트다운을 그대로 기다린다. */
    public void awaitFileWait() {
        sleep(fileWaitMs);
    }

    private void sleep(long millis) {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
