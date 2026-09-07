package com.test.test.sheetmusic.crawl;

import java.util.concurrent.Executor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * IMSLP 작업 실행기 (03 §3) — <b>단일 스레드</b>, 큐 무한.
 * 수집 워커와 단건 "파일 받아오기"가 이 실행기를 공유하므로 IMSLP 커넥션은 항상 1개다.
 */
@Configuration
@EnableAsync
public class ImslpAsyncConfig {

    @Bean("imslpExecutor")
    public Executor imslpExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(Integer.MAX_VALUE);
        executor.setThreadNamePrefix("imslp-");
        executor.setWaitForTasksToCompleteOnShutdown(false);
        executor.initialize();
        return executor;
    }
}
