package com.test.test.sheetmusic.work.dto;

import com.test.test.sheetmusic.work.Level;
import com.test.test.sheetmusic.work.WorkStatus;
import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 곡 목록(관리) 행 (02 §4-6). */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminWorkSummaryDTO {

    private Long id;
    private String titleKo;
    private String titleOriginal;
    private ComposerRefDTO composer;
    private List<String> catalogNumbers;
    private Level level;
    private int editionCount;
    private boolean hasRecommended;
    private WorkStatus status;
    private boolean needsWork;
    private boolean hidden;
    private Instant updatedAt;
}
