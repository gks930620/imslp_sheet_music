package com.test.test.sheetmusic.work.dto;

import com.test.test.sheetmusic.composer.ComposerEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 곡 응답 안의 작곡가 요약 (02 §2-1). */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComposerRefDTO {

    private Long id;
    private String nameKo;
    private String nameOriginal;

    public static ComposerRefDTO from(ComposerEntity composer) {
        return ComposerRefDTO.builder()
                .id(composer.getId())
                .nameKo(composer.getNameKo())
                .nameOriginal(composer.getNameOriginal())
                .build();
    }
}
