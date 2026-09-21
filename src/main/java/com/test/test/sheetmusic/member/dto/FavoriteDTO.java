package com.test.test.sheetmusic.member.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 즐겨찾기 켜기 응답 (02 §10-1) — {@code PUT} 은 토글이 아니라 <b>상태를 지정</b>하는 문이라
 * 몇 번을 불러도 {@code favorited: true} 다.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FavoriteDTO {

    private Long workId;
    private boolean favorited;

    public static FavoriteDTO on(Long workId) {
        return FavoriteDTO.builder().workId(workId).favorited(true).build();
    }
}
