package com.test.test.community.repository;

import com.test.test.community.CommunityEntity;
import com.test.test.community.QCommunityEntity;
import com.test.test.community.comment.QCommentEntity;
import com.test.test.community.dto.CommunityDTO;
import com.test.test.jwt.entity.QUserEntity;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class CommunityRepositoryImpl implements CommunityRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public Page<CommunityDTO> searchCommunity(String searchType, String keyword, Pageable pageable) {
        QCommunityEntity community = QCommunityEntity.communityEntity;
        QUserEntity user = QUserEntity.userEntity;
        QCommentEntity comment = QCommentEntity.commentEntity;

        // 1. 전체 카운트 조회 (user join 필요 - nickname 검색용)
        Long total = queryFactory
                .select(community.count())
                .from(community)
                .join(community.user, user)
                .where(
                        community.isDeleted.eq(false),
                        searchCondition(community, user, searchType, keyword)
                )
                .fetchOne();

        if (total == null) {
            total = 0L;
        }

        // 2. 게시글 Entity 목록 조회 (먼저 Entity를 가져와서 ID 추출)
        List<CommunityEntity> entities = queryFactory
                .selectFrom(community)
                .join(community.user, user).fetchJoin() // User Fetch Join 유지
                .where(
                        community.isDeleted.eq(false),
                        searchCondition(community, user, searchType, keyword)
                )
                .orderBy(community.createdAt.desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        // 3. ID 목록 추출
        List<Long> communityIds = entities.stream()
                .map(CommunityEntity::getId)
                .toList();

        // 4. 댓글 수 조회 (IN 쿼리 사용 - 원칙 준수)
        Map<Long, Long> commentCountMap;
        if (communityIds.isEmpty()) {
            commentCountMap = Collections.emptyMap();
        } else {
            List<Tuple> counts = queryFactory
                    .select(comment.community.id, comment.count())
                    .from(comment)
                    .where(
                            comment.community.id.in(communityIds),
                            comment.isDeleted.isFalse()
                    )
                    .groupBy(comment.community.id)
                    .fetch();

            commentCountMap = counts.stream()
                    .collect(Collectors.toMap(
                            tuple -> tuple.get(comment.community.id),
                            tuple -> tuple.get(comment.count())
                    ));
        }

        // 5. DTO 변환 및 반환
        List<CommunityDTO> content = entities.stream()
                .map(entity -> {
                    CommunityDTO dto = CommunityDTO.from(entity);
                    dto.setCommentCount(commentCountMap.getOrDefault(entity.getId(), 0L));
                    return dto;
                })
                .toList();

        return new PageImpl<>(content, pageable, total);
    }

    /**
     * 검색 조건 동적 생성
     * keyword가 null이면 null 반환 → where 조건에서 무시됨 (전체 조회)
     */
    private BooleanExpression searchCondition(QCommunityEntity community, QUserEntity user, String searchType, String keyword) {
        // keyword 또는 searchType이 없으면 조건 없음(전체 조회). switch(null) NPE 방지.
        if (keyword == null || keyword.trim().isEmpty() || searchType == null) {
            return null;
        }

        String trimmedKeyword = keyword.trim();

        // 검색 타입에 따라 조건 분기
        return switch (searchType) {
            case "title" -> community.title.containsIgnoreCase(trimmedKeyword);
            case "nickname" -> user.nickname.containsIgnoreCase(trimmedKeyword);
            default -> null; // 잘못된 타입은 조건 없음
        };
    }
}

