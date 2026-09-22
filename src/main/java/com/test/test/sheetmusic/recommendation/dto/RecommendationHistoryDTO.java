package com.test.test.sheetmusic.recommendation.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** {@code GET /api/admin/works/{workId}/recommendation-history} 응답 (02 §4-7-1) — 이력 전부, 상한 200줄. */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecommendationHistoryDTO {

    private Long workId;

    /** 실제 전체 수 — 200 을 넘어도 이 값은 진짜 전체다. */
    private long historyCount;

    /** 최신순, 최대 200줄. */
    private List<RecommendationLogDTO> history;
}
