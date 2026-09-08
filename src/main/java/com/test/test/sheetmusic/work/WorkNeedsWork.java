package com.test.test.sheetmusic.work;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.JPAExpressions;
import com.test.test.sheetmusic.edition.KoreaCopyright;
import com.test.test.sheetmusic.edition.QEditionEntity;
import java.util.ArrayList;
import java.util.List;

/**
 * 보완 필요 계산 — 규칙은 여기 한 곳에만 있다 (01_ERD §4, 2026-09-08 개정 / 기획 §11-1).
 *
 * <p>이 규칙을 쓰는 곳은 셋이고 전부 이 클래스를 부른다: 관리 홈 카운트 {@code needsWorkWorks}(02 §4-1) ·
 * 관리 곡 목록 필터 {@code status=NEEDS_WORK}(02 §4-6) · 곡 상세 {@code missing[]}(02 §4-7).
 * 한 곳에서 사라진 곡이 다른 곳에 남으면 결함이라, 두 표현(메모리 계산·질의 조건)을 한 파일에 붙여 둔다 —
 * 하나를 고치면서 다른 하나를 못 보고 지나칠 수 없게 하기 위해서다.
 *
 * <p>답하는 질문은 "필드가 비었나" 가 아니라 <b>"이 곡을 사용자에게 열어 주려면 뭐가 남았나"</b> 다.
 * 그래서 추천 판본이 있어도 판정이 {@code UNKNOWN} 이면(= 다운로드가 닫혀 있으면) 일감이다.
 * {@code RESTRICTED} 는 사람이 내린 결론이라 세지 않는다 — 할 일이 아니라 끝난 일이다.
 */
public final class WorkNeedsWork {

    private WorkNeedsWork() {
    }

    /**
     * 곡 1건의 보완 필요 항목 — 02 §4-7 의 고정 순서
     * ({@code TITLE_KO → ALIAS → LEVEL → RECOMMENDED_EDITION → COPYRIGHT_JUDGMENT}).
     * {@code RECOMMENDED_EDITION} 과 {@code COPYRIGHT_JUDGMENT} 는 추천 유무로 갈리므로 함께 나오지 않는다.
     */
    public static List<WorkMissing> missing(WorkEntity work) {
        List<WorkMissing> missing = new ArrayList<>();
        if (work.getTitleKo() == null || work.getTitleKo().isBlank()) {
            missing.add(WorkMissing.TITLE_KO);
        }
        if (work.getAliases().isEmpty()) {
            missing.add(WorkMissing.ALIAS);
        }
        if (work.getLevel() == null) {
            missing.add(WorkMissing.LEVEL);
        }
        if (work.getRecommendedEdition() == null) {
            missing.add(WorkMissing.RECOMMENDED_EDITION);
        } else if (work.getRecommendedEdition().getKoreaCopyright() == KoreaCopyright.UNKNOWN) {
            missing.add(WorkMissing.COPYRIGHT_JUDGMENT);
        }
        return missing;
    }

    /**
     * 같은 규칙의 질의 조건 — 위 {@link #missing(WorkEntity)} 가 비어 있지 않은 곡을 고른다.
     *
     * @param work        곡 별칭 {@code QWorkEntity}
     * @param recommended 그 질의가 {@code work.recommendedEdition} 에 걸어 둔 left join 별칭
     *                    (없는 별칭을 쓰면 JPQL 이 조인 없이 만들어져 결과가 달라진다)
     */
    public static BooleanExpression predicate(QWorkEntity work, QEditionEntity recommended) {
        QWorkAliasEntity alias = new QWorkAliasEntity("needsWorkAlias");
        BooleanExpression noAlias = JPAExpressions.selectOne().from(alias)
                .where(alias.work.eq(work))
                .notExists();
        return work.titleKo.isNull()
                .or(work.level.isNull())
                .or(work.recommendedEdition.isNull())
                .or(noAlias)
                .or(work.recommendedEdition.isNotNull()
                        .and(recommended.koreaCopyright.eq(KoreaCopyright.UNKNOWN)));
    }
}
