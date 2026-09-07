package com.test.test.sheetmusic.admin.dto;

import com.test.test.sheetmusic.crawl.dto.CrawlJobDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 관리 홈 숫자 카드 (02 §4-1). */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardDTO {

    private long totalWorks;
    private long readyWorks;
    private long preparingWorks;
    private long needsWorkWorks;
    private long unknownCopyrightEditions;
    private long monthlyDownloads;
    private CrawlJobDTO latestJob;
    private CrawlJobDTO activeJob;
}
