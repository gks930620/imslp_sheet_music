package com.test.test.sheetmusic.seed;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 시드 적재 실행기 (01_ERD §6, 03 §8). 빈 이름은 {@code seedLoader}.
 * {@code app.seed.enabled=false} 면 빈 자체가 만들어지지 않는다. 수집 복구({@code CrawlStartupRecovery})보다 먼저 돈다.
 */
@Component("seedLoader")
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true", matchIfMissing = true)
@Order(0)
@RequiredArgsConstructor
public class SeedLoader implements ApplicationRunner {

    private final SeedService seedService;

    @Override
    public void run(ApplicationArguments args) {
        seedService.load();
    }
}
