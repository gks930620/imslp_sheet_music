package com.test.test.sheetmusic.work.repository;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.Order;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.test.test.common.exception.BusinessRuleException;
import com.test.test.sheetmusic.composer.QComposerAliasEntity;
import com.test.test.sheetmusic.composer.QComposerEntity;
import com.test.test.sheetmusic.edition.KoreaCopyright;
import com.test.test.sheetmusic.edition.QEditionEntity;
import com.test.test.sheetmusic.work.Level;
import com.test.test.sheetmusic.work.QWorkAliasEntity;
import com.test.test.sheetmusic.work.QWorkCatalogNumberEntity;
import com.test.test.sheetmusic.work.QWorkEntity;
import com.test.test.sheetmusic.work.WorkEntity;
import com.test.test.sheetmusic.work.WorkNeedsWork;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

/** 곡 동적 조회 구현 (03 §7). 검색·작곡가의 곡·관리 목록이 조건만 바꿔 같은 쿼리를 쓴다. */
@Repository
@RequiredArgsConstructor
public class WorkRepositoryImpl implements WorkRepositoryCustom {

    private static final QWorkEntity WORK = QWorkEntity.workEntity;
    private static final QComposerEntity COMPOSER = QComposerEntity.composerEntity;
    private static final QEditionEntity RECOMMENDED = new QEditionEntity("recommendedEdition");
    private static final QWorkCatalogNumberEntity PRIMARY_CATALOG = new QWorkCatalogNumberEntity("primaryCatalog");
    private static final QWorkAliasEntity SUB_ALIAS = new QWorkAliasEntity("subAlias");
    private static final QWorkCatalogNumberEntity SUB_CATALOG = new QWorkCatalogNumberEntity("subCatalog");
    private static final QComposerAliasEntity SUB_COMPOSER_ALIAS = new QComposerAliasEntity("subComposerAlias");

    private final JPAQueryFactory queryFactory;

    @Override
    public Page<WorkEntity> search(WorkSearchCondition condition, Pageable pageable) {
        BooleanBuilder where = where(condition);

        // 둘 다 to-one 이라 fetchJoin 을 걸어도 페이징(offset/limit)이 안전하다 — 목록 20건마다 붙던
        // 작곡가·추천 판본 지연 로딩을 없앤다(03 §7 3번 N+1).
        JPAQuery<WorkEntity> query = queryFactory.selectFrom(WORK)
                .join(WORK.composer, COMPOSER).fetchJoin()
                .leftJoin(WORK.recommendedEdition, RECOMMENDED).fetchJoin();
        if (condition.getSortOrder() == WorkSortOrder.OPUS) {
            query.leftJoin(PRIMARY_CATALOG)
                    .on(PRIMARY_CATALOG.work.eq(WORK), PRIMARY_CATALOG.sortOrder.eq(0));
        }

        List<WorkEntity> content = query.where(where)
                .orderBy(orderSpecifiers(condition))
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        return new PageImpl<>(content, pageable, count(condition));
    }

    @Override
    public long count(WorkSearchCondition condition) {
        Long total = queryFactory.select(WORK.count())
                .from(WORK)
                .join(WORK.composer, COMPOSER)
                .leftJoin(WORK.recommendedEdition, RECOMMENDED)
                .where(where(condition))
                .fetchOne();
        return total == null ? 0L : total;
    }

    /**
     * 02 §3-2 — 자격 곡(READY + 한국어 제목)을 먼저 채우고, 모자란 칸만 준비 중 곡으로 채운다.
     * <b>자격 곡이 0개면 폴백하지 않는다</b>(화면이 영역 자체를 감춘다). RESTRICTED·UNKNOWN 은 폴백에도 쓰지 않는다.
     */
    @Override
    public List<WorkEntity> findPopular(int limit) {
        List<WorkEntity> eligible = popularQuery(readyPredicate(), limit);
        if (eligible.isEmpty() || eligible.size() >= limit) {
            return eligible;
        }
        List<WorkEntity> result = new ArrayList<>(eligible);
        result.addAll(popularQuery(preparingPredicate(), limit - eligible.size()));
        return result;
    }

    @Override
    public long countNeedsWork() {
        Long total = queryFactory.select(WORK.count())
                .from(WORK)
                .leftJoin(WORK.recommendedEdition, RECOMMENDED)
                .where(WorkNeedsWork.predicate(WORK, RECOMMENDED))
                .fetchOne();
        return total == null ? 0L : total;
    }

    /** 자격·폴백이 같은 정렬을 쓴다 — 다른 것은 상태 조건 하나뿐이다(02 §3-2 3번). */
    private List<WorkEntity> popularQuery(BooleanExpression statusPredicate, int limit) {
        return queryFactory.selectFrom(WORK)
                .join(WORK.composer, COMPOSER).fetchJoin()
                .leftJoin(WORK.recommendedEdition, RECOMMENDED).fetchJoin()
                .where(WORK.hidden.isFalse(), hasKoreanTitle(), statusPredicate)
                .orderBy(WORK.downloadCount.desc(), levelOrder().asc(), WORK.titleKo.asc(), WORK.id.asc())
                .limit(limit)
                .fetch();
    }

    private BooleanExpression hasKoreanTitle() {
        return WORK.titleKo.isNotNull().and(WORK.titleKo.trim().ne(""));
    }

    /**
     * 난이도 오름차순은 <b>enum 선언 순서(ordinal)가 아니라 명시적 순서</b>다 — 값은 문자열로 저장되므로
     * DB 정렬은 알파벳순(ADVANCED 가 1등)이 되고, enum 에 값을 하나 끼워 넣으면 ordinal 정렬도 조용히 바뀐다.
     * NULL(미정)은 맨 뒤다.
     */
    private NumberExpression<Integer> levelOrder() {
        return new CaseBuilder()
                .when(WORK.level.eq(Level.BEGINNER)).then(0)
                .when(WORK.level.eq(Level.ELEMENTARY)).then(1)
                .when(WORK.level.eq(Level.INTERMEDIATE)).then(2)
                .when(WORK.level.eq(Level.ADVANCED)).then(3)
                .otherwise(4);
    }

    // ===== where =====

    private BooleanBuilder where(WorkSearchCondition condition) {
        BooleanBuilder where = new BooleanBuilder();
        if (!condition.isIncludeHidden()) {
            where.and(WORK.hidden.isFalse());
        }
        if (condition.getComposerId() != null) {
            where.and(WORK.composer.id.eq(condition.getComposerId()));
        }
        if (condition.getTerms() != null) {
            for (String term : condition.getTerms()) {
                where.and(termMatches(term));
            }
        }
        if (condition.isLevelNone()) {
            where.and(WORK.level.isNull());
        } else if (condition.getLevels() != null && !condition.getLevels().isEmpty()) {
            where.and(WORK.level.in(condition.getLevels()));
        }
        if (condition.getPages() != null) {
            where.and(RECOMMENDED.pageCount.isNotNull());
            if (condition.getPages().getMin() != null) {
                where.and(RECOMMENDED.pageCount.goe(condition.getPages().getMin()));
            }
            if (condition.getPages().getMax() != null) {
                where.and(RECOMMENDED.pageCount.loe(condition.getPages().getMax()));
            }
        }
        if (condition.isDownloadableOnly()) {
            where.and(readyPredicate());
        }
        if (condition.getStatusFilter() != null && !condition.getStatusFilter().isBlank()) {
            where.and(statusPredicate(condition.getStatusFilter()));
        }
        return where;
    }

    /** 02 §3-1 2번: 곡 제목·별칭·작품번호·작곡가 이름·작곡가 별칭 중 하나에 부분 일치. */
    private BooleanExpression termMatches(String term) {
        return WORK.titleKoNormalized.contains(term)
                .or(WORK.titleOriginalNormalized.contains(term))
                .or(COMPOSER.nameKoNormalized.contains(term))
                .or(COMPOSER.nameOriginalNormalized.contains(term))
                .or(aliasContains(term))
                .or(catalogContains(term))
                .or(composerAliasContains(term));
    }

    private BooleanExpression aliasContains(String term) {
        return JPAExpressions.selectOne().from(SUB_ALIAS)
                .where(SUB_ALIAS.work.eq(WORK), SUB_ALIAS.aliasNormalized.contains(term))
                .exists();
    }

    private BooleanExpression catalogContains(String term) {
        return JPAExpressions.selectOne().from(SUB_CATALOG)
                .where(SUB_CATALOG.work.eq(WORK), SUB_CATALOG.catalogValueNormalized.contains(term))
                .exists();
    }

    private BooleanExpression composerAliasContains(String term) {
        return JPAExpressions.selectOne().from(SUB_COMPOSER_ALIAS)
                .where(SUB_COMPOSER_ALIAS.composer.eq(COMPOSER), SUB_COMPOSER_ALIAS.aliasNormalized.contains(term))
                .exists();
    }

    private BooleanExpression readyPredicate() {
        return RECOMMENDED.pdfFileId.isNotNull().and(RECOMMENDED.koreaCopyright.eq(KoreaCopyright.FREE));
    }

    private BooleanExpression preparingPredicate() {
        return WORK.recommendedEdition.isNull().or(RECOMMENDED.pdfFileId.isNull());
    }

    private BooleanExpression statusPredicate(String statusFilter) {
        return switch (statusFilter) {
            case "READY" -> readyPredicate();
            case "PREPARING" -> preparingPredicate();
            case "RESTRICTED" -> RECOMMENDED.pdfFileId.isNotNull()
                    .and(RECOMMENDED.koreaCopyright.eq(KoreaCopyright.RESTRICTED));
            case "UNKNOWN" -> RECOMMENDED.pdfFileId.isNotNull()
                    .and(RECOMMENDED.koreaCopyright.eq(KoreaCopyright.UNKNOWN));
            case "HIDDEN" -> WORK.hidden.isTrue();
            case "NEEDS_WORK" -> WorkNeedsWork.predicate(WORK, RECOMMENDED);
            default -> throw new BusinessRuleException("상태 값이 올바르지 않아요: " + statusFilter);
        };
    }

    // ===== order by =====

    private OrderSpecifier<?>[] orderSpecifiers(WorkSearchCondition condition) {
        List<OrderSpecifier<?>> orders = new ArrayList<>();
        WorkSortOrder sortOrder = condition.getSortOrder() == null ? WorkSortOrder.RELEVANCE : condition.getSortOrder();
        switch (sortOrder) {
            case RELEVANCE -> {
                orders.add(relevance(condition.getWholeTerm()).desc());
                orders.add(WORK.downloadCount.desc());
                orders.add(WORK.id.asc());
            }
            case DOWNLOADS -> {
                orders.add(WORK.downloadCount.desc());
                orders.add(WORK.id.asc());
            }
            case OPUS -> {
                orders.add(new OrderSpecifier<>(Order.ASC, PRIMARY_CATALOG.sortKey,
                        OrderSpecifier.NullHandling.NullsLast));
                orders.add(WORK.titleOriginal.asc());
                orders.add(WORK.id.asc());
            }
            case UPDATED -> {
                orders.add(WORK.updatedAt.desc());
                orders.add(WORK.id.desc());
            }
        }
        return orders.toArray(new OrderSpecifier<?>[0]);
    }

    /** 02 §3-1 4번 일치도: 제목·별칭 정확 3 / 전방 2 / 그 외 1. */
    private NumberExpression<Integer> relevance(String wholeTerm) {
        if (wholeTerm == null || wholeTerm.isEmpty()) {
            return com.querydsl.core.types.dsl.Expressions.numberTemplate(Integer.class, "1");
        }
        BooleanExpression exactAlias = JPAExpressions.selectOne().from(SUB_ALIAS)
                .where(SUB_ALIAS.work.eq(WORK), SUB_ALIAS.aliasNormalized.eq(wholeTerm))
                .exists();
        BooleanExpression prefixAlias = JPAExpressions.selectOne().from(SUB_ALIAS)
                .where(SUB_ALIAS.work.eq(WORK), SUB_ALIAS.aliasNormalized.startsWith(wholeTerm))
                .exists();
        return new CaseBuilder()
                .when(WORK.titleKoNormalized.eq(wholeTerm).or(exactAlias)).then(3)
                .when(WORK.titleKoNormalized.startsWith(wholeTerm).or(prefixAlias)).then(2)
                .otherwise(1);
    }
}
