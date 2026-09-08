package com.test.test.sheetmusic.work.dto;

import com.test.test.sheetmusic.edition.EditionEntity;
import com.test.test.sheetmusic.edition.EditionKind;
import com.test.test.sheetmusic.edition.EditionScope;
import com.test.test.sheetmusic.work.ScopeNoteCode;
import com.test.test.sheetmusic.work.WorkEntity;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * "받게 되는 악보가 찾은 것과 어떻게 다른가" 한 줄을 만들 재료 (02 §2-2-1, 2026-09-08 신설).
 *
 * <p>묶음("더 크다")과 편곡·악장("작거나 다르다")을 한 필드로 통합한다 — 사용자에게는 같은 질문의 답이고
 * 화면에서 같은 자리·같은 줄을 쓰기 때문이다. <b>문구는 서버가 만들지 않는다</b>(코드만 준다).
 *
 * <p>{@code codes} 는 절대 빈 배열이 아니다 — 할 말이 없으면 이 객체 자체가 {@code null} 이다
 * (빈 상태를 두 가지로 만들지 않는다).
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScopeNoteDTO {

    /** 고정 순서 {@code COLLECTION → ARRANGEMENT → MOVEMENT_ONLY}. */
    private List<ScopeNoteCode> codes;

    /** {@code MOVEMENT_ONLY} 일 때 추천 판본의 악장 번호(수집이 못 읽었으면 null). 그 외 항상 null. */
    private Integer movementNumber;

    /**
     * 곡 하나의 범위 한 줄 — {@code WorkSummaryDTO} 를 만드는 <b>모든</b> 응답이 이 함수를 쓴다
     * (검색·인기곡·작곡가의 곡·같은 작곡가 곡). 붙일 코드가 하나도 없으면 {@code null} 을 돌려준다.
     */
    public static ScopeNoteDTO from(WorkEntity work) {
        EditionEntity recommended = work.getRecommendedEdition();
        List<ScopeNoteCode> codes = new ArrayList<>();
        if (work.getCollectionGuide() != null && !work.getCollectionGuide().isBlank()) {
            codes.add(ScopeNoteCode.COLLECTION);
        }
        // 추천 판본이 없으면 "무엇을 받게 되는지" 를 판단할 근거가 없다 — COLLECTION 만 나올 수 있다.
        if (recommended != null && recommended.getKind() == EditionKind.ARRANGEMENT) {
            codes.add(ScopeNoteCode.ARRANGEMENT);
        }
        boolean movementOnly = recommended != null && recommended.getScope() == EditionScope.MOVEMENT;
        if (movementOnly) {
            codes.add(ScopeNoteCode.MOVEMENT_ONLY);
        }
        if (codes.isEmpty()) {
            return null;
        }
        return ScopeNoteDTO.builder()
                .codes(codes)
                .movementNumber(movementOnly ? recommended.getMovementNumber() : null)
                .build();
    }
}
