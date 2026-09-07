package com.test.test.sheetmusic.work.repository;

import com.test.test.sheetmusic.work.Level;
import com.test.test.sheetmusic.work.PagesFilter;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 곡 조회 조건 (02 §3-1 검색, §3-8 작곡가의 곡, §4-6 관리 목록이 같은 쿼리를 조건만 바꿔 쓴다).
 * DTO 는 record 가 아니라 class (컨벤션 §0).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkSearchCondition {

    /** 정규화된 검색어 단어들. 비어 있으면 검색어 조건 없음. */
    private List<String> terms;

    /** 전체 검색어를 공백 없이 정규화한 값 — 일치도 계산용. */
    private String wholeTerm;

    private List<Level> levels;

    /** 관리 목록의 level=NONE (난이도 미정만). */
    private boolean levelNone;

    private PagesFilter pages;

    private boolean downloadableOnly;

    private Long composerId;

    /** true 면 숨김 곡도 포함(관리 목록). */
    private boolean includeHidden;

    /** 관리 목록 상태 필터: READY / PREPARING / RESTRICTED / UNKNOWN / NEEDS_WORK / HIDDEN. */
    private String statusFilter;

    private WorkSortOrder sortOrder;

    /** 필터(난이도·쪽수·바로받기·상태)를 뺀 조건 — unfilteredTotal 계산용. */
    public WorkSearchCondition withoutFilters() {
        return WorkSearchCondition.builder()
                .terms(terms)
                .wholeTerm(wholeTerm)
                .composerId(composerId)
                .includeHidden(includeHidden)
                .sortOrder(sortOrder)
                .build();
    }

    public boolean hasFilters() {
        return (levels != null && !levels.isEmpty()) || levelNone || pages != null
                || downloadableOnly || (statusFilter != null && !statusFilter.isBlank());
    }
}
