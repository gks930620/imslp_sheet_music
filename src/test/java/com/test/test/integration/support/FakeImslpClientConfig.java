package com.test.test.integration.support;

import com.test.test.sheetmusic.crawl.ImslpGate;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * IMSLP 를 대신하는 테스트 대역 묶음.
 *
 * <ul>
 *   <li>{@link FakeImslpClient} 를 {@code @Primary} 로 등록해 실제 {@code HttpImslpClient} 대신 주입한다.</li>
 *   <li>{@link RecordingImslpGate} 를 {@code @Primary} 로 등록해 {@code ImslpGate} 호출을 관찰한다
 *       (동작은 실제 게이트 그대로 — 테스트 설정이 0ms 라 자지 않는다).</li>
 *   <li>둘은 {@link ImslpCallLog} 하나를 공유해 "파일 대기 → 파일 수신" 순서를 한 줄로 검증할 수 있게 한다.</li>
 * </ul>
 *
 * 수집·파일 받아오기 테스트는 {@code @Import(FakeImslpClientConfig.class)} 로 붙인다 ({@link CrawlTestSupport} 가 이미 포함).
 */
@TestConfiguration
public class FakeImslpClientConfig {

    @Bean
    public ImslpCallLog imslpCallLog() {
        return new ImslpCallLog();
    }

    @Bean
    @Primary
    public ImslpGate recordingImslpGate(ImslpCallLog imslpCallLog) {
        return new RecordingImslpGate(imslpCallLog);
    }

    @Bean
    @Primary
    public FakeImslpClient fakeImslpClient(ImslpCallLog imslpCallLog) {
        return new FakeImslpClient(imslpCallLog);
    }
}
