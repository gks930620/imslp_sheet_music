package com.test.test.sheetmusic.work.dto;

import com.test.test.common.dto.PageResponse;
import com.test.test.sheetmusic.composer.dto.ComposerCardDTO;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 검색 응답 (02 §3-1). */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkSearchResponseDTO {

    private String q;
    /** 관용 처리가 끝난 확정 검색 기준 — {@code in=xyz} 로 와도 {@code "ALL"}. 화면 세그먼트는 이 값으로 그린다(인수 조건 8-F 4). */
    private String in;
    private List<ComposerCardDTO> composers;
    private long composerMatchCount;
    private long unfilteredTotal;
    /**
     * 0건 화면 [B] 판정 재료 (02 §3-1, 화면정의 08 §4-7) — 같은 구분·같은 검색어·기준 ALL·필터 없음의 곡 수.
     * {@code in=ALL} 이면 null(질문이 성립하지 않는다). 화면이 "모름"(null) 과 "0건"(0) 을 절대 같게 취급하지 않으므로
     * 셋(양수 / 0 / null)을 구분한다 — 키를 빼지 않고 null 로 내려 보낸다.
     */
    private Long totalInAll;
    private PageResponse<WorkSummaryDTO> works;
}
