package com.test.test.sheetmusic.admin;

import com.test.test.sheetmusic.admin.dto.DashboardDTO;
import com.test.test.sheetmusic.crawl.CrawlJobDtoAssembler;
import com.test.test.sheetmusic.crawl.CrawlJobEntity;
import com.test.test.sheetmusic.crawl.CrawlJobStatus;
import com.test.test.sheetmusic.crawl.repository.CrawlJobRepository;
import com.test.test.sheetmusic.edition.repository.DownloadLogRepository;
import com.test.test.sheetmusic.edition.repository.EditionRepository;
import com.test.test.sheetmusic.work.repository.WorkRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 관리 홈 (02 §4-1). */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardService {

    private final WorkRepository workRepository;
    private final EditionRepository editionRepository;
    private final DownloadLogRepository downloadLogRepository;
    private final CrawlJobRepository crawlJobRepository;
    private final CrawlJobDtoAssembler crawlJobDtoAssembler;

    /** "이번 달" 경계 기준 시간대 (03 §11). */
    @Value("${app.timezone:Asia/Seoul}")
    private String timezone;

    public DashboardDTO summary() {
        CrawlJobEntity latest = crawlJobRepository.findFirstByOrderByCreatedAtDescIdDesc().orElse(null);
        CrawlJobEntity active = crawlJobRepository
                .findByStatuses(List.of(CrawlJobStatus.RUNNING, CrawlJobStatus.PAUSED))
                .stream().findFirst().orElse(null);

        return DashboardDTO.builder()
                .totalWorks(workRepository.count())
                .readyWorks(workRepository.countReady())
                .preparingWorks(workRepository.countPreparing())
                .needsWorkWorks(workRepository.countNeedsWork())
                .needsRecommendationReviewWorks(workRepository.countNeedsRecommendationReview())
                .unknownCopyrightEditions(editionRepository.countUnknownCopyright())
                .monthlyDownloads(downloadLogRepository.countSince(startOfMonth()))
                .latestJob(latest == null ? null : crawlJobDtoAssembler.toDto(latest))
                .activeJob(active == null ? null : crawlJobDtoAssembler.toDto(active))
                .build();
    }

    /** Asia/Seoul 기준 이번 달 1일 00:00 을 UTC 로 환산 (01_ERD §3-7). */
    private Instant startOfMonth() {
        ZoneId zone = ZoneId.of(timezone);
        return LocalDate.now(zone).withDayOfMonth(1).atStartOfDay(zone).toInstant();
    }
}
