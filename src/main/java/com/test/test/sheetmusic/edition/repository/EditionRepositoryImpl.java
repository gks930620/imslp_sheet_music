package com.test.test.sheetmusic.edition.repository;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.Order;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.test.test.sheetmusic.composer.QComposerEntity;
import com.test.test.sheetmusic.edition.EditionEntity;
import com.test.test.sheetmusic.edition.KoreaCopyright;
import com.test.test.sheetmusic.edition.QEditionEntity;
import com.test.test.sheetmusic.work.QWorkEntity;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class EditionRepositoryImpl implements EditionRepositoryCustom {

    private static final QEditionEntity EDITION = QEditionEntity.editionEntity;
    private static final QWorkEntity WORK = QWorkEntity.workEntity;
    private static final QComposerEntity COMPOSER = QComposerEntity.composerEntity;

    private final JPAQueryFactory queryFactory;

    @Override
    public Page<EditionEntity> findPending(String normalizedQuery, Long composerId, Pageable pageable) {
        BooleanBuilder where = new BooleanBuilder();
        where.and(EDITION.koreaCopyright.eq(KoreaCopyright.UNKNOWN));
        if (composerId != null) {
            where.and(COMPOSER.id.eq(composerId));
        }
        if (normalizedQuery != null && !normalizedQuery.isEmpty()) {
            where.and(WORK.titleKoNormalized.contains(normalizedQuery)
                    .or(WORK.titleOriginalNormalized.contains(normalizedQuery))
                    .or(COMPOSER.nameKoNormalized.contains(normalizedQuery))
                    .or(COMPOSER.nameOriginalNormalized.contains(normalizedQuery)));
        }

        List<EditionEntity> content = queryFactory.selectFrom(EDITION)
                .join(EDITION.work, WORK)
                .join(WORK.composer, COMPOSER)
                .where(where)
                .orderBy(new OrderSpecifier<>(Order.ASC, COMPOSER.deathYear, OrderSpecifier.NullHandling.NullsLast),
                        EDITION.id.asc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        Long total = queryFactory.select(EDITION.count())
                .from(EDITION)
                .join(EDITION.work, WORK)
                .join(WORK.composer, COMPOSER)
                .where(where)
                .fetchOne();

        return new PageImpl<>(content, pageable, total == null ? 0L : total);
    }
}
