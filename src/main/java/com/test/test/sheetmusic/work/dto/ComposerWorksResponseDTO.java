package com.test.test.sheetmusic.work.dto;

import com.test.test.common.dto.PageResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 작곡가의 곡 목록 응답 (02 §3-8). */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComposerWorksResponseDTO {

    private long unfilteredTotal;
    private PageResponse<WorkSummaryDTO> works;
}
