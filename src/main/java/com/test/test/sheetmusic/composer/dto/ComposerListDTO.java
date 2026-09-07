package com.test.test.sheetmusic.composer.dto;

import com.test.test.sheetmusic.composer.ComposerEntity;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 작곡가 전체 목록 (02 §3-5). 페이지 없음. */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComposerListDTO {

    private int total;
    private List<Item> composers;

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Item {
        private Long id;
        private String nameKo;
        private String nameOriginal;
        private Integer birthYear;
        private Integer deathYear;
        private long workCount;

        public static Item from(ComposerEntity composer, long workCount) {
            return Item.builder()
                    .id(composer.getId())
                    .nameKo(composer.getNameKo())
                    .nameOriginal(composer.getNameOriginal())
                    .birthYear(composer.getBirthYear())
                    .deathYear(composer.getDeathYear())
                    .workCount(workCount)
                    .build();
        }
    }
}
