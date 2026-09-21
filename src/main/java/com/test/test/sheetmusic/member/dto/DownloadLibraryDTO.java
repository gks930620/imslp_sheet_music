package com.test.test.sheetmusic.member.dto;

import com.test.test.common.dto.PageResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 내 악보 › 받은 악보 탭 (02 §10-3). */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DownloadLibraryDTO {

    private LibraryCountsDTO counts;
    private PageResponse<ReceivedWorkDTO> items;
}
