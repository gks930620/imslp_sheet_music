package com.test.test.sheetmusic.crawl.dto;

import com.test.test.sheetmusic.crawl.CrawlFailReason;
import com.test.test.sheetmusic.crawl.CrawlItemMode;
import com.test.test.sheetmusic.crawl.CrawlItemStatus;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 수집 항목 (02 §6-6). */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CrawlItemDTO {

    private Long id;
    private int seq;
    private String url;
    private CrawlItemMode mode;
    private CrawlItemStatus status;
    private CrawlFailReason failReason;
    private String message;
    private Long workId;
    private String workTitle;
    private Integer editionCount;
    private Integer fileCount;
    private Instant startedAt;
    private Instant finishedAt;
}
