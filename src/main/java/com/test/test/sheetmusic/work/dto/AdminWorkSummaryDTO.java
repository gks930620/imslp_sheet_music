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
    /**
     * 지금 추천이 사람 눈을 통과했는가 (02 §4-6, 2026-09-08 신설) — 목록의 "미검수" 표시.
     * {@code hasRecommended == false} 인 곡은 항상 false 다(검수할 대상이 없다).
     */
    private boolean recommendationReviewed;
    private WorkStatus status;
    private boolean needsWork;
    private boolean hidden;
    private Instant updatedAt;
}
