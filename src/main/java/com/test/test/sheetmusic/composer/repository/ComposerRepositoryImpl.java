package com.test.test.sheetmusic.composer.repository;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.test.test.sheetmusic.composer.ComposerEntity;
import com.test.test.sheetmusic.composer.QComposerAliasEntity;
import com.test.test.sheetmusic.composer.QComposerEntity;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ComposerRepositoryImpl implements ComposerRepositoryCustom {

    private static final QComposerEntity COMPOSER = QComposerEntity.composerEntity;
    private static final QComposerAliasEntity ALIAS = new QComposerAliasEntity("composerAliasSub");

    private final JPAQueryFactory queryFactory;

    @Override
    public Page<ComposerEntity> searchAdmin(String normalizedQuery, boolean missingKo, Pageable pageable) {
        BooleanBuilder where = new BooleanBuilder();
        if (normalizedQuery != null && !normalizedQuery.isEmpty()) {
            where.and(matches(normalizedQuery));
        }
        if (missingKo) {
            where.and(COMPOSER.nameKo.isNull());
        }

        List<ComposerEntity> content = queryFactory.selectFrom(COMPOSER)
                .where(where)
                .orderBy(COMPOSER.nameOriginal.asc(), COMPOSER.id.asc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        Long total = queryFactory.select(COMPOSER.count()).from(COMPOSER).where(where).fetchOne();
        return new PageImpl<>(content, pageable, total == null ? 0L : total);
    }

    @Override
    public List<Long> findMatchingIds(List<String> terms) {
        if (terms == null || terms.isEmpty()) {
            return List.of();
        }
        BooleanBuilder where = new BooleanBuilder();
        for (String term : terms) {
            where.and(matches(term));
        }
        return queryFactory.select(COMPOSER.id).from(COMPOSER).where(where).fetch();
    }

    private BooleanExpression matches(String term) {
        BooleanExpression aliasMatch = JPAExpressions.selectOne().from(ALIAS)
                .where(ALIAS.composer.eq(COMPOSER), ALIAS.aliasNormalized.contains(term))
                .exists();
        return COMPOSER.nameKoNormalized.contains(term)
                .or(COMPOSER.nameOriginalNormalized.contains(term))
                .or(aliasMatch);
    }
}
