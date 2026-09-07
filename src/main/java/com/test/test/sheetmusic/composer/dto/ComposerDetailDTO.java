package com.test.test.sheetmusic.composer.dto;

import com.test.test.sheetmusic.composer.ComposerAliasEntity;
import com.test.test.sheetmusic.composer.ComposerEntity;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 작곡가 상세(공개) (02 §3-7). */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComposerDetailDTO {

    private Long id;
    private String nameKo;
    private String nameOriginal;
    private Integer birthYear;
    private Integer deathYear;
    private String nationality;
    private List<String> aliases;
    private String imslpUrl;
    private long workCount;

    public static ComposerDetailDTO from(ComposerEntity composer, long workCount) {
        return ComposerDetailDTO.builder()
                .id(composer.getId())
                .nameKo(composer.getNameKo())
                .nameOriginal(composer.getNameOriginal())
                .birthYear(composer.getBirthYear())
                .deathYear(composer.getDeathYear())
                .nationality(composer.getNationality())
                .aliases(composer.getAliases().stream().map(ComposerAliasEntity::getAlias).toList())
                .imslpUrl(composer.getImslpUrl())
                .workCount(workCount)
                .build();
    }
}
