package com.test.test.sheetmusic.work.repository;

import com.test.test.common.exception.BusinessRuleException;

/** 곡 목록 정렬 (02 §3-1 검색 일치도, §3-2 인기, §3-8 sort, §4-6 관리). */
public enum WorkSortOrder {

    /** 일치도 → download_count → id (검색). */
    RELEVANCE,
    /** download_count DESC, id ASC (작곡가의 곡 기본). */
    DOWNLOADS,
    /** 대표 작품번호 sort_key ASC NULLS LAST → title_original ASC. */
    OPUS,
    /** updated_at DESC, id DESC (관리 목록). */
    UPDATED;

    /** 02 §3-8 sort 쿼리 값 → enum. 정의되지 않은 값은 400. */
    public static WorkSortOrder fromComposerWorksSort(String value) {
        if (value == null || value.isBlank()) {
            return DOWNLOADS;
        }
        return switch (value.trim()) {
            case "downloads" -> DOWNLOADS;
            case "opus" -> OPUS;
            default -> throw new BusinessRuleException("정렬 값이 올바르지 않아요: " + value);
        };
    }
}
