package com.test.test.sheetmusic.work.repository;

import com.test.test.sheetmusic.work.WorkEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** 곡 동적 조회 (03 §7 — QueryDSL + 정규화 컬럼 LIKE). */
public interface WorkRepositoryCustom {

    Page<WorkEntity> search(WorkSearchCondition condition, Pageable pageable);

    long count(WorkSearchCondition condition);
}
