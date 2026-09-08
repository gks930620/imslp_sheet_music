package com.test.test.sheetmusic.crawl;

import com.test.test.sheetmusic.crawl.repository.CrawlItemRepository;
import com.test.test.sheetmusic.crawl.repository.CrawlJobRepository;
import com.test.test.sheetmusic.edition.EditionEntity;
import com.test.test.sheetmusic.edition.FileFetchStatus;
import com.test.test.sheetmusic.edition.repository.EditionRepository;
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

    /** 되돌린 사유는 관리자 화면(§5-4 {@code fileFetchError})에 그대로 보인다. */
    private static final String STUCK_FETCH_MESSAGE = "서비스가 다시 시작돼 받아오기가 중단됐어요. 다시 요청해 주세요";

    private final CrawlJobRepository crawlJobRepository;
    private final CrawlItemRepository crawlItemRepository;
    private final EditionRepository editionRepository;

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
        releaseStuckFileFetches();
    }

    /**
     * '받아오는 중'에 갇힌 판본 풀기 (02 §5-7, 01_ERD §7 — 2026-09-07).
     *
     * <p>{@code file_fetch_status} 가 {@code QUEUED/FETCHING} 인 채 프로세스가 죽으면 비동기 작업은
     * 프로세스와 함께 사라지는데 상태 컬럼만 남는다. §5-7 은 그 상태에서 409 를 내므로 그 판본은
     * <b>DB 를 손으로 고치기 전까지 영구히 받아올 수 없다</b>. 수집 항목을 INTERRUPTED 로 되돌리는 것과 같은 이유로
     * {@code FAILED} + 사유로 되돌려 관리자가 다시 요청할 수 있게 한다.
     */
    private void releaseStuckFileFetches() {
        List<EditionEntity> stuck = editionRepository
                .findByFileFetchStatuses(List.of(FileFetchStatus.QUEUED, FileFetchStatus.FETCHING));
        for (EditionEntity edition : stuck) {
            edition.markFetchFailed(STUCK_FETCH_MESSAGE);
        }
        if (!stuck.isEmpty()) {
            log.info("재시작으로 중단된 판본 파일 받아오기 {}건을 FAILED 로 되돌렸습니다 - editionIds: {}",
                    stuck.size(), stuck.stream().map(EditionEntity::getId).toList());
        }
    }
}
