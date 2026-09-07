package com.test.test.sheetmusic.crawl;

import com.test.test.sheetmusic.crawl.repository.CrawlItemRepository;
import com.test.test.sheetmusic.crawl.repository.CrawlJobRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 서비스 재시작 복구 (02 §6-10, 03 §3).
 * RUNNING/PAUSED 작업은 {@code STOPPED, stoppedByRestart=true} 로, 처리 중이던 항목은 {@code FAILED/INTERRUPTED} 로.
 * 자동 재개하지 않는다 — 관리자가 "이어서 시작"을 누른다.
 */
@Component
@Order(10)
@RequiredArgsConstructor
@Slf4j
public class CrawlStartupRecovery implements ApplicationRunner {

    private final CrawlJobRepository crawlJobRepository;
    private final CrawlItemRepository crawlItemRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        for (CrawlItemEntity item : crawlItemRepository.findProcessing()) {
            item.markFailed(CrawlFailReason.INTERRUPTED, item.getWorkId(), item.getEditionCount(),
                    item.getFileCount());
            crawlJobRepository.findById(item.getJobId())
                    .ifPresent(job -> job.countResult(CrawlItemStatus.FAILED));
        }
        List<CrawlJobEntity> active = crawlJobRepository
                .findByStatuses(List.of(CrawlJobStatus.RUNNING, CrawlJobStatus.PAUSED));
        for (CrawlJobEntity job : active) {
            job.markStopped(true);
        }
        if (!active.isEmpty()) {
            log.info("재시작으로 중지된 수집 작업 {}건", active.size());
        }
    }
}
