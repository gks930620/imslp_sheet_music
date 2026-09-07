package com.test.test.sheetmusic.composer.dto;

import com.test.test.sheetmusic.composer.ComposerEntity;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 작곡가 목록(관리) 행 (02 §4-2). */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminComposerDTO {

    private Long id;
    private String nameKo;
    private String nameOriginal;
    private Integer birthYear;
    private Integer deathYear;
    private long workCount;
    private Instant updatedAt;

    public static AdminComposerDTO from(ComposerEntity composer, long workCount) {
        return AdminComposerDTO.builder()
                .id(composer.getId())
                .nameKo(composer.getNameKo())
                .nameOriginal(composer.getNameOriginal())
                .birthYear(composer.getBirthYear())
                .deathYear(composer.getDeathYear())
                .workCount(workCount)
                .updatedAt(composer.getUpdatedAt())
                .build();
    }
}
