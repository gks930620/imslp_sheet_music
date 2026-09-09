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
    /**
     * "추천 판본 확인 필요 N곡" (02 §4-1, 2026-09-08 신설) — 추천 있음 + 미검수, <b>숨김 곡 제외</b>.
     * 공개(출시) 기준(기획 §8-17)을 재는 지표라 공개 대상이 아닌 숨김 곡은 세지 않는다.
     * 보완 필요({@code needsWorkWorks})와 섞지 않는다 — 미검수 곡은 이미 열려 있다.
     */
    private long needsRecommendationReviewWorks;
    private long unknownCopyrightEditions;
    private long monthlyDownloads;
    private CrawlJobDTO latestJob;
    private CrawlJobDTO activeJob;
}
