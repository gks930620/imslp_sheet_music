package com.test.test.sheetmusic.work.dto;

import com.test.test.common.dto.PageResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 곡 목록(관리) 응답 (02 §4-6). */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminWorkListDTO {

    private long unfilteredTotal;
    private PageResponse<AdminWorkSummaryDTO> works;
}
