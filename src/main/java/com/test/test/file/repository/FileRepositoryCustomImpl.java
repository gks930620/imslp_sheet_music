package com.test.test.file.repository;

import static com.test.test.file.entity.QFileEntity.fileEntity;

import com.test.test.file.entity.FileEntity;
import com.test.test.file.entity.RefType;
import com.test.test.file.entity.Usage;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


@RequiredArgsConstructor
public class FileRepositoryCustomImpl implements FileRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public List<FileEntity> findOrphansOlderThan(LocalDateTime threshold) {
        // refId 가 null 또는 0 (= 미연결) 이고, 생성 시각이 유예 기준(threshold) 이전인 파일만.
        // ⚠️ "refId=0 전부 삭제" 금지 — 반드시 오래된 것(createdAt < threshold)만.
        return queryFactory
                .selectFrom(fileEntity)
                .where(
                        fileEntity.refId.isNull().or(fileEntity.refId.eq(0L)),
                        fileEntity.createdAt.lt(threshold)
                )
                .fetch();
    }

    @Override
    public List<FileEntity> searchFiles(Long refId, RefType refType, Usage fileUsage) {
        return queryFactory
                .selectFrom(fileEntity)
                .where(
                        eqRefId(refId),
                        eqRefType(refType),
                        eqFileUsage(fileUsage)
                )
                .fetch();
    }

    @Override
    public Map<Long, String> findFilePathMapByRefIds(List<Long> refIds, RefType refType, Usage fileUsage) {
        // IN 쿼리로 한 번에 조회
        List<FileEntity> files = queryFactory
                .selectFrom(fileEntity)
                .where(
                        fileEntity.refId.in(refIds),
                        eqRefType(refType),
                        eqFileUsage(fileUsage)
                )
                .fetch();

        // Map으로 변환 (각 refId당 첫 번째 파일)
        return files.stream()
                .collect(Collectors.toMap(
                        FileEntity::getRefId,
                        // DB에 저장된 웹 경로 (/uploads/{저장파일명})
                        FileEntity::getFilePath,
                        (existing, replacement) -> existing // 중복 시 첫 번째 유지
                ));
    }

    // 동적 조건 메서드들
    private BooleanExpression eqRefId(Long refId) {
        return refId != null ? fileEntity.refId.eq(refId) : null;
    }

    private BooleanExpression eqRefType(RefType refType) {
        return refType != null ? fileEntity.refType.eq(refType) : null;
    }

    private BooleanExpression eqFileUsage(Usage fileUsage) {
        return fileUsage != null ? fileEntity.fileUsage.eq(fileUsage) : null;
    }
}
