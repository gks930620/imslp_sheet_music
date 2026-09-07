package com.test.test.sheetmusic.crawl;

import com.test.test.common.exception.BusinessRuleException;
import com.test.test.common.exception.DuplicateResourceException;
import com.test.test.common.exception.EntityNotFoundException;
import com.test.test.sheetmusic.common.ImslpUrlNormalizer;
import com.test.test.sheetmusic.crawl.dto.CrawlCheckDTOs;
import com.test.test.sheetmusic.crawl.dto.CrawlJobDTO;
import com.test.test.sheetmusic.crawl.repository.CrawlItemRepository;
import com.test.test.sheetmusic.crawl.repository.CrawlJobRepository;
import com.test.test.sheetmusic.edition.repository.EditionRepository;
import com.test.test.sheetmusic.work.WorkEntity;
import com.test.test.sheetmusic.work.repository.WorkRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** 수집 작업 관리 (02 §6-1 ~ §6-2, §6-7 ~ §6-9). */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CrawlService {

    /** 02 §6-1 예상 시간 — 항목당 초. */
    private static final long SECONDS_WITH_FILES = 75L;
    private static final long SECONDS_METADATA_ONLY = 6L;

    private static final String VERDICT_NEW = "NEW";
    private static final String VERDICT_ATTACH = "ATTACH";
    private static final String VERDICT_EXISTS = "EXISTS";
    private static final String VERDICT_INVALID = "INVALID_URL";
    private static final String VERDICT_DUPLICATE = "DUPLICATE";

    private final CrawlJobRepository crawlJobRepository;
    private final CrawlItemRepository crawlItemRepository;
    private final WorkRepository workRepository;
    private final EditionRepository editionRepository;
    private final CrawlJobDtoAssembler crawlJobDtoAssembler;
    private final CrawlWorker crawlWorker;

    // ===== §6-1 주소 확인 =====

    public CrawlCheckDTOs.CheckResult check(List<String> urls, boolean fetchFiles) {
        List<CrawlCheckDTOs.CheckItem> items = new ArrayList<>();
        Map<String, Integer> seenCanonical = new LinkedHashMap<>();
        int seq = 0;
        int newCount = 0;
        int attachCount = 0;
        int existsCount = 0;
        int invalidCount = 0;
        int duplicateCount = 0;

        for (String rawUrl : urls) {
            if (rawUrl == null || rawUrl.isBlank()) {
                continue;
            }
            String inputUrl = rawUrl.trim();
            seq++;
            String canonical = ImslpUrlNormalizer.canonicalize(inputUrl);
            if (canonical == null) {
                items.add(item(seq, inputUrl, null, VERDICT_INVALID, null, null));
                invalidCount++;
                continue;
            }
            Integer duplicateOf = seenCanonical.get(canonical);
            if (duplicateOf != null) {
                items.add(item(seq, inputUrl, canonical, VERDICT_DUPLICATE, null, duplicateOf));
                duplicateCount++;
                continue;
            }
            seenCanonical.put(canonical, seq);

            Verdict verdict = verdictOf(canonical);
            items.add(item(seq, inputUrl, canonical, verdict.name, verdict.workId, null));
            switch (verdict.name) {
                case VERDICT_NEW -> newCount++;
                case VERDICT_ATTACH -> attachCount++;
                default -> existsCount++;
            }
        }

        long perItem = fetchFiles ? SECONDS_WITH_FILES : SECONDS_METADATA_ONLY;
        return CrawlCheckDTOs.CheckResult.builder()
                .items(items)
                .summary(CrawlCheckDTOs.CheckSummary.builder()
                        .newCount(newCount)
                        .attachCount(attachCount)
                        .existsCount(existsCount)
                        .invalidCount(invalidCount)
                        .duplicateCount(duplicateCount)
                        .estimatedSeconds((long) (newCount + attachCount) * perItem)
                        .build())
                .build();
    }

    // ===== §6-2 작업 생성 =====

    @Transactional
    public CrawlJobDTO createJob(List<CrawlCheckDTOs.JobItemRequest> requestItems, boolean fetchFiles,
                                 String createdBy) {
        requireNoActiveJob();

        List<PlannedItem> planned = plan(requestItems);
        if (planned.stream().noneMatch(PlannedItem::isProcessable)) {
            throw new BusinessRuleException("수집할 주소가 없어요");
        }
        return startJob(planned, fetchFiles, createdBy, null);
    }

    // ===== §6-7 중지 =====

    @Transactional
    public CrawlJobDTO stop(Long jobId) {
        CrawlJobEntity job = findJob(jobId);
        if (job.getStatus() == CrawlJobStatus.RUNNING) {
            job.requestStop();
        } else if (job.getStatus() == CrawlJobStatus.PAUSED) {
            job.markStopped(false);
        } else {
            throw new BusinessRuleException("중지할 수 있는 상태가 아니에요");
        }
        return crawlJobDtoAssembler.toDto(job);
    }

    // ===== §6-8 이어서 시작 =====

    @Transactional
    public CrawlJobDTO resume(Long jobId) {
        CrawlJobEntity job = findJob(jobId);
        if (job.getStatus() == CrawlJobStatus.RUNNING) {
            throw new BusinessRuleException("이미 진행 중이에요");
        }
        boolean wasPaused = job.getStatus() == CrawlJobStatus.PAUSED;
        if (!wasPaused) {
            requireNoActiveJob();
            if (crawlItemRepository.countByJobIdAndStatus(jobId, CrawlItemStatus.PENDING) == 0) {
                throw new BusinessRuleException("이어서 할 주소가 없어요");
            }
        }
        job.resume();
        if (!wasPaused) {
            // 일시 정지 중인 작업은 워커가 살아 있으므로 다시 제출하지 않는다.
            submitAfterCommit(jobId);
        }
        return crawlJobDtoAssembler.toDto(job);
    }

    // ===== §6-9 실패한 것만 다시 =====

    @Transactional
    public CrawlJobDTO retryFailed(Long jobId, String createdBy) {
        CrawlJobEntity job = findJob(jobId);
        requireNoActiveJob();

        List<CrawlItemEntity> failed =
                crawlItemRepository.findByJobIdAndStatusOrderBySeqAsc(jobId, CrawlItemStatus.FAILED);
        if (failed.isEmpty()) {
            throw new BusinessRuleException("다시 시도할 실패 항목이 없어요");
        }
        List<CrawlCheckDTOs.JobItemRequest> requestItems = new ArrayList<>();
        for (CrawlItemEntity item : failed) {
            requestItems.add(new CrawlCheckDTOs.JobItemRequest(item.getImslpUrl(), false));
        }
        List<PlannedItem> planned = plan(requestItems);
        if (planned.isEmpty()) {
            throw new BusinessRuleException("수집할 주소가 없어요");
        }
        return startJob(planned, job.isFetchFiles(), createdBy, jobId);
    }

    // ===== 내부 =====

    private CrawlJobDTO startJob(List<PlannedItem> planned, boolean fetchFiles, String createdBy, Long retryOfJobId) {
        CrawlJobEntity job = CrawlJobEntity.builder()
                .fetchFiles(fetchFiles)
                .totalCount(planned.size())
                .createdBy(createdBy)
                .retryOfJobId(retryOfJobId)
                .build();
        crawlJobRepository.save(job);

        int seq = 0;
        for (PlannedItem plannedItem : planned) {
            seq++;
            CrawlItemEntity item = CrawlItemEntity.builder()
                    .jobId(job.getId())
                    .seq(seq)
                    .imslpUrl(plannedItem.canonicalUrl)
                    .itemMode(plannedItem.mode)
                    .workId(plannedItem.workId)
                    .build();
            if (plannedItem.mode == CrawlItemMode.SKIP) {
                item.markSkipped(plannedItem.workId);
                job.countResult(CrawlItemStatus.SKIPPED);
            }
            crawlItemRepository.save(item);
        }

        submitAfterCommit(job.getId());
        return crawlJobDtoAssembler.toDto(job);
    }

    private List<PlannedItem> plan(List<CrawlCheckDTOs.JobItemRequest> requestItems) {
        List<PlannedItem> planned = new ArrayList<>();
        Map<String, Boolean> seen = new LinkedHashMap<>();
        for (CrawlCheckDTOs.JobItemRequest request : requestItems) {
            if (request == null || request.getUrl() == null || request.getUrl().isBlank()) {
                continue;
            }
            String canonical = ImslpUrlNormalizer.canonicalize(request.getUrl().trim());
            if (canonical == null || seen.containsKey(canonical)) {
                continue;
            }
            seen.put(canonical, Boolean.TRUE);

            Verdict verdict = verdictOf(canonical);
            CrawlItemMode mode = switch (verdict.name) {
                case VERDICT_NEW -> CrawlItemMode.CREATE;
                case VERDICT_ATTACH -> CrawlItemMode.ATTACH;
                default -> request.isRefresh() ? CrawlItemMode.REFRESH : CrawlItemMode.SKIP;
            };
            planned.add(new PlannedItem(canonical, mode, verdict.workId));
        }
        return planned;
    }

    /** 02 §6-1 5번 — 수집된 판본이 있으면 EXISTS, 곡만 있으면 ATTACH, 없으면 NEW. */
    private Verdict verdictOf(String canonicalUrl) {
        WorkEntity work = workRepository.findByImslpUrl(canonicalUrl).orElse(null);
        if (work == null) {
            return new Verdict(VERDICT_NEW, null);
        }
        boolean crawled = editionRepository.countCrawledEditions(work.getId()) > 0;
        return new Verdict(crawled ? VERDICT_EXISTS : VERDICT_ATTACH, work.getId());
    }

    private void requireNoActiveJob() {
        boolean active = !crawlJobRepository
                .findByStatuses(List.of(CrawlJobStatus.RUNNING, CrawlJobStatus.PAUSED)).isEmpty();
        if (active) {
            throw new DuplicateResourceException("진행 중인 수집이 있어요");
        }
    }

    private CrawlJobEntity findJob(Long jobId) {
        return crawlJobRepository.findById(jobId)
                .orElseThrow(() -> EntityNotFoundException.of("수집 작업", jobId));
    }

    private void submitAfterCommit(Long jobId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    crawlWorker.run(jobId);
                }
            });
        } else {
            crawlWorker.run(jobId);
        }
    }

    private static CrawlCheckDTOs.CheckItem item(int seq, String inputUrl, String canonicalUrl, String verdict,
                                                 Long existingWorkId, Integer duplicateOfSeq) {
        return CrawlCheckDTOs.CheckItem.builder()
                .seq(seq)
                .inputUrl(inputUrl)
                .canonicalUrl(canonicalUrl)
                .verdict(verdict)
                .existingWorkId(existingWorkId)
                .duplicateOfSeq(duplicateOfSeq)
                .build();
    }

    /** 판정 결과(내부). */
    private static final class Verdict {
        private final String name;
        private final Long workId;

        private Verdict(String name, Long workId) {
            this.name = name;
            this.workId = workId;
        }
    }

    /** 작업 생성 계획(내부). */
    private static final class PlannedItem {
        private final String canonicalUrl;
        private final CrawlItemMode mode;
        private final Long workId;

        private PlannedItem(String canonicalUrl, CrawlItemMode mode, Long workId) {
            this.canonicalUrl = canonicalUrl;
            this.mode = mode;
            this.workId = workId;
        }

        private boolean isProcessable() {
            return mode != CrawlItemMode.SKIP;
        }
    }
}
