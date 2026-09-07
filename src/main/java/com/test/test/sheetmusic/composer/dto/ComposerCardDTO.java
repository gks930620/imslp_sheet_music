package com.test.test.sheetmusic.composer.dto;

import com.test.test.sheetmusic.composer.ComposerEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 작곡가 카드 (02 §3-1 검색 결과 상단, §3-6 홈 바로가기). */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComposerCardDTO {

    private Long id;
    private String nameKo;
    private String nameOriginal;
    private long workCount;

    public static ComposerCardDTO from(ComposerEntity composer, long workCount) {
        return ComposerCardDTO.builder()
                .id(composer.getId())
                .nameKo(composer.getNameKo())
                .nameOriginal(composer.getNameOriginal())
                .workCount(workCount)
                .build();
    }
}
