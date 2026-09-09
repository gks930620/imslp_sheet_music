package com.test.test.sheetmusic.work;

import com.querydsl.core.types.dsl.BooleanExpression;

/**
 * 추천 판본 미검수 계산 — 규칙은 여기 한 곳에만 있다 (01_ERD §3-3, 02 §4-1 · §4-6 · §5-6-1 / 기획 §10-8).
 *
 * <p>쓰는 곳은 둘이고 <b>둘 다 이 클래스를 부른다</b>: 관리 홈 카드 {@code needsRecommendationReviewWorks}(02 §4-1) ·
 * 곡 목록 필터 {@code status=NEEDS_RECOMMENDATION_REVIEW}(02 §4-6). 카드 숫자와 목록이 어긋나면
 * 관리자는 어느 쪽도 믿지 않으므로, 두 표현(메모리 계산·질의 조건)을 한 파일에 붙여 둔다.
 *
 * <p>답하는 질문은 <b>"지금 추천이 사람 눈을 통과했나"</b> 다. 그래서 추천이 없는 곡은 대상이 아니고,
 * <b>숨긴 곡도 뺀다</b> — 이 값은 공개(출시) 기준(기획 §8-17 "추천 판본 미검수 0곡")을 재는 지표이고
 * 숨긴 곡은 공개 대상이 아니다.
 *
 * <p>보완 필요({@link WorkNeedsWork})와 <b>섞지 않는다</b>: 보완 필요는 "열어 주려면 뭐가 남았나" 인데
 * 미검수 곡은 이미 열려 있다(바로 받기 가능). 한 숫자로 뭉개면 §8-17 의 두 조건을 따로 볼 수 없다.
 */
public final class WorkRecommendationReview {

    private WorkRecommendationReview() {
    }

    /** 곡 1건이 검수를 기다리는가 — 숨김은 여기서 보지 않는다(모집단 조건은 {@link #predicate}). */
    public static boolean needsReview(WorkEntity work) {
        return work.getRecommendedEdition() != null && !work.isRecommendedEditionReviewed();
    }

    /** 같은 규칙의 질의 조건 — 관리 홈 카드와 곡 목록 필터가 같은 모집단(추천 있음 + 미검수 + 숨김 제외)을 쓴다. */
    public static BooleanExpression predicate(QWorkEntity work) {
        return work.recommendedEdition.isNotNull()
                .and(work.recommendedEditionReviewed.isFalse())
                .and(work.hidden.isFalse());
    }
}
