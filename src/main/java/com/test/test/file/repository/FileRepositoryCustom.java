package com.test.test.file.repository;

import com.test.test.file.entity.FileEntity;
import com.test.test.file.entity.RefType;
import com.test.test.file.entity.Usage;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public interface FileRepositoryCustom {

    /**
     * orphan(버려진 임시) 파일 조회 — 아직 어떤 글에도 연결되지 않았고(refId=0/null),
     * 생성 후 기준시각(threshold) 이전에 만들어진 파일. (§5-3-1 ③ 배치 정리 대상)
     * @param threshold 이 시각 이전에 생성된 것만 대상 (= now - graceHours)
     * @return 정리 대상 파일 목록
     */
    List<FileEntity> findOrphansOlderThan(LocalDateTime threshold);
    /**
     * 동적 조건으로 파일 검색
     * @param refId 참조 ID
     * @param refType 참조 타입
     * @param fileUsage 파일 용도 (선택)
     * @return 파일 엔티티 리스트
     */
    List<FileEntity> searchFiles(Long refId, RefType refType, Usage fileUsage);

    /**
     * 여러 refId의 파일을 조회하여 Map으로 반환 (N+1 방지)
     * @param refIds 참조 ID 리스트
     * @param refType 참조 타입
     * @param fileUsage 파일 용도
     * @return Map<refId, filePath> (각 refId당 첫 번째 파일 경로)
     */
    Map<Long, String> findFilePathMapByRefIds(List<Long> refIds, RefType refType, Usage fileUsage);
}

