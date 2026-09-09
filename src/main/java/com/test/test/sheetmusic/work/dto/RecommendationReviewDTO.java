package com.test.test.sheetmusic.work.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 추천 판본 확인함/되돌리기 요청 (02 §5-6-1). DTO 는 record 가 아니라 class (컨벤션 §0). */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RecommendationReviewDTO {

    @NotNull(message = "확인 여부를 선택해 주세요")
    private Boolean reviewed;
}
