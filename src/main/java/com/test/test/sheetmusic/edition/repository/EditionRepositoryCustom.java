package com.test.test.sheetmusic.edition.repository;

import com.test.test.sheetmusic.edition.EditionEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** 저작권 판정 대기함 조회 (02 §5-8). */
public interface EditionRepositoryCustom {

    Page<EditionEntity> findPending(String normalizedQuery, Long composerId, Pageable pageable);
}
