package com.test.test.file.repository;

import com.test.test.file.entity.FileEntity;
import com.test.test.file.entity.RefType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public interface FileRepository extends JpaRepository<FileEntity, Long>, FileRepositoryCustom {
    // N+1 방지를 위한 IN 쿼리
    List<FileEntity> findByRefIdInAndRefType(List<Long> refIds, RefType refType);

    /** 특정 글에 연결된 파일 전체(IMAGES+ATTACHMENT). 글 삭제 연동(§5-3-1 ②)에서 사용. */
    List<FileEntity> findByRefIdAndRefType(Long refId, RefType refType);

    /**
     * 본문 reconcile(§5-3-1 ①)에서 사용: 아직 글에 연결되지 않은 임시 이미지(refId=0) 중
     * 본문에 실제로 참조된 저장파일명들만 조회 → 글ID로 연결(link)한다.
     */
    List<FileEntity> findByFileUsageAndRefIdAndStoredFileNameIn(
            com.test.test.file.entity.Usage fileUsage, Long refId, java.util.Collection<String> storedFileNames);

    /**
     * 여러 refId의 파일 URL을 Map으로 반환 (N+1 방지용 편의 메서드)
     * @param refIds 참조 ID 리스트
     * @param refType 참조 타입
     * @return Map<refId, List<fileUrl>>
     */
    default Map<Long, List<String>> findFileUrlsMapByRefIds(List<Long> refIds, RefType refType) {
        if (refIds == null || refIds.isEmpty()) {
            return Map.of();
        }

        return findByRefIdInAndRefType(refIds, refType).stream()
                .collect(Collectors.groupingBy(
                        FileEntity::getRefId,
                        Collectors.mapping(
                                // DB에 저장된 웹 경로 (/uploads/{저장파일명})
                                FileEntity::getFilePath,
                                Collectors.toList()
                        )
                ));
    }
}
