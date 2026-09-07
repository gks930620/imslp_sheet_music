package com.test.test.integration.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * {@link FakeImslpClient} 를 {@code @Primary} 로 등록해 실제 {@code HttpImslpClient} 대신 주입한다.
 * 수집·파일 받아오기 테스트는 {@code @Import(FakeImslpClientConfig.class)} 로 붙인다 ({@link CrawlTestSupport} 가 이미 포함).
 */
@TestConfiguration
public class FakeImslpClientConfig {

    @Bean
    @Primary
    public FakeImslpClient fakeImslpClient() {
        return new FakeImslpClient();
    }
}
