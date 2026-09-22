package com.test.test.sheetmusic.recommendation.dto;

import com.test.test.sheetmusic.edition.EditionKind;
import com.test.test.sheetmusic.edition.EditionScope;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 이력 한 줄의 판본 쪽(edition/previousEdition) — <b>그때의 표기 스냅샷</b>이다(01_ERD §3-13, 02 §4-7-2).
 * 지금 판본을 다시 읽은 값이 아니다.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecommendationEditionRefDTO {

    /** 판본이 삭제됐으면 null. 나머지 6개는 스냅샷이라 그대로 남는다. */
    private Long editionId;
    private EditionKind kind;
    private EditionScope scope;
    private Integer movementNumber;
    private String publisher;
    private String editor;
    private Integer publishYear;
}
