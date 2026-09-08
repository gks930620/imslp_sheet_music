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

    /**
     * 직전 요청 <b>시작</b> 시각 기준 최소 간격을 채운다. 커넥션은 항상 1개라 동기화만으로 충분하다.
     *
     * <p>⚠️ "완료" 가 아니라 "시작" 기준이다 — {@code lastRequestAt} 을 대기 직후(요청을 내보내기 직전)에 찍는다.
     * 그래서 8MB 파일을 60초 걸려 받은 뒤 다시 부르면 남은 간격이 음수라 <b>대기 없이 통과</b>한다.
     * 페이지 요청(수백 ms)에서는 완료 기준과 차이가 없고, 전송이 오래 걸리는 파일 요청은
     * {@link #awaitFileWait()} 15초가 앞에 붙어 간격이 보장된다({@code EditionFileFetcher.downloadAndStore}).
     * (2026-09-07: 이 javadoc 이 "완료 기준"이라고 거짓말을 하고 있어 페이싱 부재를 가렸다 — 코드에 맞춰 정정.)
     */
    public synchronized void awaitRequestSlot() {
        if (requestIntervalMs <= 0) {
            lastRequestAt = System.currentTimeMillis();
            return;
        }
        long wait = lastRequestAt + requestIntervalMs - System.currentTimeMillis();
        sleep(wait);
        lastRequestAt = System.currentTimeMillis();
    }

    /**
     * 파일 대기 페이지의 카운트다운을 그대로 기다린다 — 우회하지 않는다(기획 §9-1).
     *
     * <p>자리는 {@code EditionFileFetcher.downloadAndStore} 한 곳뿐이고(03 §3), 그 안에서의 위치도 계약이다 —
     * {@code ImslpClient.resolveFileUrl()}(대기 페이지 GET) <b>다음</b>, {@code downloadResolvedFile()}(파일 GET) <b>앞</b>.
     * 파일 1개당 정확히 1회다(항목당 1회가 아니다).
     *
     * <p>대기 페이지를 열기 <b>전</b>에 부르면 브라우저와 순서가 뒤집힌다 — 카운트다운을 띄운 페이지를 받아 놓고
     * 0.3초 만에 파일을 치게 되어, 서버에는 정확히 "카운트다운을 안 기다린 클라이언트"로 보인다 (2026-09-07 정정).
     */
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
