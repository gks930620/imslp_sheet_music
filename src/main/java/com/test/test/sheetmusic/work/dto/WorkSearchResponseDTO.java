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
    private List<ComposerCardDTO> composers;
    private long composerMatchCount;
    private long unfilteredTotal;
    private PageResponse<WorkSummaryDTO> works;
}
