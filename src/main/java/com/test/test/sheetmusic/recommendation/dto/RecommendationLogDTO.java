package com.test.test.sheetmusic.recommendation.dto;

import com.test.test.sheetmusic.recommendation.RecommendationAction;
import com.test.test.sheetmusic.recommendation.RecommendationClearedReason;
import com.test.test.sheetmusic.recommendation.RecommendationReason;
import com.test.test.sheetmusic.recommendation.RecommendationSource;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 이력 한 줄 (01_ERD §3-13, 02 §4-7-2). */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecommendationLogDTO {

    private Long id;
    private Instant decidedAt;
    private RecommendationSource source;
    private RecommendationAction action;

    /** {@code source = ADMIN} 일 때만. 닉네임이다 — 로그인 아이디를 내려보내지 않는다. */
    private String decidedByNickname;

    /** {@code ASSIGNED} 면 값, {@code CLEARED} 면 null. */
    private RecommendationEditionRefDTO edition;

    /** null = 처음 지정. */
    private RecommendationEditionRefDTO previousEdition;

    /** {@code source = AUTO} 일 때만. */
    private RecommendationAutoDTO auto;

    /** {@code ADMIN} + {@code ASSIGNED} 일 때만. */
    private RecommendationReason reason;

    private String note;

    /** {@code CLEARED} 일 때만. */
    private RecommendationClearedReason clearedReason;
}
