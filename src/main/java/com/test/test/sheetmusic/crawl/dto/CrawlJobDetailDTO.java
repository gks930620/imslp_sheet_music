package com.test.test.sheetmusic.crawl.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/** 수집 작업 상세 (02 §6-6) = {@link CrawlJobDTO} + 항목 전부(seq 순). */
@Getter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class CrawlJobDetailDTO extends CrawlJobDTO {

    private List<CrawlItemDTO> items;
}
