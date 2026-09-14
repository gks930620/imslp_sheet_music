package com.test.test.sheetmusic.work.repository;

import com.test.test.sheetmusic.work.Level;
import com.test.test.sheetmusic.work.PagesFilter;
import com.test.test.sheetmusic.work.SearchIn;
import com.test.test.sheetmusic.work.Section;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 곡 조회 조건 (02 §3-1 검색, §3-8 작곡가의 곡, §4-6 관리 목록이 같은 쿼리를 조건만 바꿔 쓴다).
 * DTO 는 record 가 아니라 class (컨벤션 §0).
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkSearchCondition {

    /** 정규화된 검색어 단어들. 비어 있으면 검색어 조건 없음. */
    private List<String> terms;

    /** 전체 검색어를 공백 없이 정규화한 값 — 일치도 계산용. */
    private String wholeTerm;

    /** 검색 기준 (02 §3-1 {@code in}) — 단어가 찾는 칸을 정한다. null 이면 ALL. */
    private SearchIn searchIn;

    /**
     * 악기 구분 (02 §0-7). 공개 API 는 항상 채운다. <b>null 이면 구분을 걸지 않는다</b> —
     * 관리 목록(§4-6)은 모든 구분을 한 화면에서 본다(기획 04 §3-6).
     */
    private Section section;

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

    /** 필터(난이도·쪽수·바로받기·상태)를 뺀 조건 — unfilteredTotal 계산용. 검색어·기준·구분은 그대로다. */
    public WorkSearchCondition withoutFilters() {
        return WorkSearchCondition.builder()
                .terms(terms)
                .wholeTerm(wholeTerm)
                .searchIn(searchIn)
                .section(section)
                .composerId(composerId)
                .includeHidden(includeHidden)
                .sortOrder(sortOrder)
                .build();
    }

    /**
     * 같은 구분 · 같은 검색어 · 기준 ALL · 필터 없음 — {@code totalInAll} 계산용 (02 §3-1).
     * 0건 화면 [B] 는 필터를 다 푼 뒤에만 나오므로 필터를 넣어 세면 [A] 를 지나온 화면에서만 맞는 숫자가 된다.
     */
    public WorkSearchCondition allScopeWithoutFilters() {
        return WorkSearchCondition.builder()
                .terms(terms)
                .wholeTerm(wholeTerm)
                .searchIn(SearchIn.ALL)
                .section(section)
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
