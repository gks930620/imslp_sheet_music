package com.test.test.sheetmusic.member.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 내 악보 탭 머리의 숫자 2개 (02 §10-2 · §10-3, 화면정의 09 §6 S3).
 *
 * <p><b>어느 탭에 있든 둘 다 준다</b> — 화면이 탭 머리에 두 숫자를 함께 그리기 때문이다.
 * 값은 숨김 제외 전체 수이고 {@code page}·{@code size} 와 무관하며, 각 탭의 {@code totalElements} 와 항상 같다.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LibraryCountsDTO {

    private long favorites;
    private long downloads;
}
