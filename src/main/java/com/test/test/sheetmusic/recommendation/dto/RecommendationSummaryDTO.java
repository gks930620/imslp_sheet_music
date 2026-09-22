package com.test.test.sheetmusic.recommendation.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * "이 판본을 고른 이유" + "바뀐 이력" 최근 5줄 (01_ERD §3-13, 02 §4-7-2). 곡 상세({@code AdminWorkDetailDTO})
 * 안에 객체 하나로 들어간다 — 추천·기록이 없어도 <b>객체는 항상 있다</b>({@code current: null, history: []}).
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecommendationSummaryDTO {

    /** 맨 위 줄이 {@code ASSIGNED} 면 그 줄, {@code CLEARED} 거나 줄이 없으면 null. */
    private RecommendationLogDTO current;

    private long historyCount;

    /** 최신순 최대 5줄. {@code history[0]} 은 {@code current} 와 같은 줄이다(의도된 중복 — 03 §30-1). */
    private List<RecommendationLogDTO> history;

    private boolean hasMore;
}
