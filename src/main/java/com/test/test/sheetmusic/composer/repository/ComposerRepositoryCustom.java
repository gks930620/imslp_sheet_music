package com.test.test.sheetmusic.composer.repository;

import com.test.test.sheetmusic.composer.ComposerEntity;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** 작곡가 동적 조회 (02 §3-1 작곡가 카드, §4-2 관리 목록). */
public interface ComposerRepositoryCustom {

    Page<ComposerEntity> searchAdmin(String normalizedQuery, boolean missingKo, Pageable pageable);

    /** 모든 단어가 한글·원어 표기 또는 별칭에 부분 일치하는 작곡가 id (02 §3-1 5번). */
    List<Long> findMatchingIds(List<String> terms);
}
