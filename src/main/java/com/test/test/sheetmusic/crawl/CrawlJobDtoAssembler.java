package com.test.test.sheetmusic.crawl;

import com.test.test.sheetmusic.crawl.dto.CrawlItemDTO;
import com.test.test.sheetmusic.crawl.dto.CrawlJobDTO;
import com.test.test.sheetmusic.crawl.dto.CrawlJobDetailDTO;
import com.test.test.sheetmusic.crawl.repository.CrawlItemRepository;
import com.test.test.sheetmusic.work.WorkEntity;
import com.test.test.sheetmusic.work.repository.WorkRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 수집 작업 → 응답 DTO (02 §6-3·§6-6). */
@Component
@RequiredArgsConstructor
public class CrawlJobDtoAssembler {

    /** 처리된 항목이 없을 때 쓰는 항목당 예상 시간(초) — 02 §6-3. */
    public static final long DEFAULT_SECONDS_PER_ITEM = 75L;

    private final CrawlItemRepository crawlItemRepository;
    private final WorkRepository workRepository;

    public CrawlJobDTO toDto(CrawlJobEntity job) {
        CrawlJobDTO.CrawlJobDTOBuilder<?, ?> builder = CrawlJobDTO.builder();
        fill(builder, job);
        return builder.build();
    }

    public CrawlJobDetailDTO toDetailDto(CrawlJobEntity job) {
        List<CrawlItemEntity> items = crawlItemRepository.findByJobIdOrderBySeqAsc(job.getId());
        Map<Long, String> titles = workTitles(items);
        List<CrawlItemDTO> itemDtos = new ArrayList<>();
        for (CrawlItemEntity item : items) {
            itemDtos.add(CrawlItemDTO.builder()
                    .id(item.getId())
                    .seq(item.getSeq())
                    .url(item.getImslpUrl())
                    .mode(item.getItemMode())
                    .status(item.getStatus())
                    .failReason(item.getFailReason())
                    .message(item.getMessage())
                    .workId(item.getWorkId())
                    .workTitle(item.getWorkId() == null ? null : titles.get(item.getWorkId()))
                    .editionCount(item.getEditionCount())
                    .fileCount(item.getFileCount())
                    .startedAt(item.getStartedAt())
                    .finishedAt(item.getFinishedAt())
                    .build());
        }
        CrawlJobDetailDTO.CrawlJobDetailDTOBuilder<?, ?> builder = CrawlJobDetailDTO.builder();
        fill(builder, job);
        return builder.items(itemDtos).build();
    }

    private void fill(CrawlJobDTO.CrawlJobDTOBuilder<?, ?> builder, CrawlJobEntity job) {
        int processed = job.processedCount();
        int pending = Math.max(0, job.getTotalCount() - processed);
        CrawlJobDTO.CurrentItem currentItem = currentItem(job);
        builder.id(job.getId());
        builder.status(job.getStatus());
        builder.stopRequested(job.isStopRequested());
        builder.stoppedByRestart(job.isStoppedByRestart());
        builder.fetchFiles(job.isFetchFiles());
        builder.totalCount(job.getTotalCount());
        builder.processedCount(processed);
        builder.successCount(job.getSuccessCount());
        builder.failCount(job.getFailCount());
        builder.skipCount(job.getSkipCount());
        builder.hiddenCount(job.getHiddenCount());
        builder.pendingCount(pending);
        builder.currentItem(currentItem);
        builder.currentStage(currentItem == null ? null : job.getCurrentStage());
        builder.currentFileIndex(currentItem == null ? null : job.getCurrentFileIndex());
        builder.currentFileTotal(currentItem == null ? null : job.getCurrentFileTotal());
        builder.pausedUntil(job.getPausedUntil());
        builder.failureReason(job.getFailureReason());
        builder.retryOfJobId(job.getRetryOfJobId());
        builder.createdBy(job.getCreatedBy());
        builder.createdAt(job.getCreatedAt());
        builder.startedAt(job.getStartedAt());
        builder.finishedAt(job.getFinishedAt());
        builder.elapsedSeconds(elapsedSeconds(job));
        builder.estimatedRemainingSeconds(estimatedRemainingSeconds(job, processed, pending));
    }

    private CrawlJobDTO.CurrentItem currentItem(CrawlJobEntity job) {
        if (job.getCurrentItemId() == null || !job.isActive()) {
            return null;
        }
        return crawlItemRepository.findById(job.getCurrentItemId())
                .map(item -> CrawlJobDTO.CurrentItem.builder()
                        .seq(item.getSeq())
                        .url(item.getImslpUrl())
                        .title(titleOf(item))
                        .build())
                .orElse(null);
    }

    private String titleOf(CrawlItemEntity item) {
        if (item.getWorkId() != null) {
            return workRepository.findById(item.getWorkId()).map(WorkEntity::getTitleOriginal).orElse(null);
        }
        String title = com.test.test.sheetmusic.common.ImslpUrlNormalizer.titleOf(item.getImslpUrl());
        if (title == null) {
            return null;
        }
        String readable = title.replace('_', ' ');
        int paren = readable.lastIndexOf(" (");
        return paren > 0 ? readable.substring(0, paren) : readable;
    }

    private Long elapsedSeconds(CrawlJobEntity job) {
        if (job.getStartedAt() == null) {
            return null;
        }
        Instant end = job.getFinishedAt() == null ? Instant.now() : job.getFinishedAt();
        return Math.max(0L, Duration.between(job.getStartedAt(), end).getSeconds());
    }

    private Long estimatedRemainingSeconds(CrawlJobEntity job, int processed, int pending) {
        if (!job.isActive()) {
            return null;
        }
        if (pending <= 0) {
            return 0L;
        }
        long done = (long) job.getSuccessCount() + job.getFailCount() + job.getHiddenCount();
        if (done <= 0 || job.getStartedAt() == null) {
            return pending * DEFAULT_SECONDS_PER_ITEM;
        }
        long elapsed = Math.max(1L, Duration.between(job.getStartedAt(), Instant.now()).getSeconds());
        return Math.max(1L, elapsed / done) * pending;
    }

    private Map<Long, String> workTitles(List<CrawlItemEntity> items) {
        List<Long> ids = items.stream().map(CrawlItemEntity::getWorkId).filter(java.util.Objects::nonNull).toList();
        Map<Long, String> titles = new HashMap<>();
        if (ids.isEmpty()) {
            return titles;
        }
        for (WorkEntity work : workRepository.findAllById(ids)) {
            titles.put(work.getId(), work.getTitleOriginal());
        }
        return titles;
    }
}
