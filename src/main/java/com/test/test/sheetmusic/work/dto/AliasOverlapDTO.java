package com.test.test.sheetmusic.work.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 별칭 겹침 (02 §4-10). */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AliasOverlapDTO {

    private String alias;
    private long overlapCount;
}
