package com.test.test.sheetmusic.composer.dto;

import com.test.test.sheetmusic.composer.ComposerAliasEntity;
import com.test.test.sheetmusic.composer.ComposerEntity;
import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 작곡가 상세(관리) (02 §4-3). */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminComposerDetailDTO {

    private Long id;
    private String nameKo;
    private String nameOriginal;
    private List<String> aliases;
    private Integer birthYear;
    private Integer deathYear;
    private String nationality;
    private String imslpUrl;
    private long workCount;
    private Instant createdAt;
    private Instant updatedAt;

    public static AdminComposerDetailDTO from(ComposerEntity composer, long workCount) {
        return AdminComposerDetailDTO.builder()
                .id(composer.getId())
                .nameKo(composer.getNameKo())
                .nameOriginal(composer.getNameOriginal())
                .aliases(composer.getAliases().stream().map(ComposerAliasEntity::getAlias).toList())
                .birthYear(composer.getBirthYear())
                .deathYear(composer.getDeathYear())
                .nationality(composer.getNationality())
                .imslpUrl(composer.getImslpUrl())
                .workCount(workCount)
                .createdAt(composer.getCreatedAt())
                .updatedAt(composer.getUpdatedAt())
                .build();
    }
}
