package com.test.test.sheetmusic.recommendation.dto;

import com.test.test.sheetmusic.recommendation.RecommendationAutoRule;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 자동 지정 근거 — <b>지정 시점 값</b>으로 박아 둔 것(기획 06 §1-3, 02 §4-7-2). {@code source = AUTO} 일 때만. */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecommendationAutoDTO {

    private RecommendationAutoRule rule;

    /** null = "IMSLP 다운로드 수가 적혀 있지 않은 판본". */
    private Integer imslpDownloadCount;

    /** 1 이면 화면이 "1위" 라고 쓰지 않고 "이것 하나뿐" 이라고 말한다. */
    private Integer candidateCount;

    private Integer rank;
}
