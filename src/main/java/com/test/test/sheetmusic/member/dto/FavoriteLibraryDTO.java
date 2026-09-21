package com.test.test.sheetmusic.member.dto;

import com.test.test.common.dto.PageResponse;
import com.test.test.sheetmusic.work.dto.WorkSummaryDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 내 악보 › 즐겨찾기 탭 (02 §10-2).
 * 곡 카드는 다른 목록과 <b>한 글자도 다르지 않다</b> — {@code matchedAlias} 는 null, {@code scopeNote} 는 §2-2-1.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FavoriteLibraryDTO {

    private LibraryCountsDTO counts;
    private PageResponse<WorkSummaryDTO> works;
}
