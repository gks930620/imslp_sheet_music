package com.test.test.sheetmusic.work.dto;

import com.test.test.sheetmusic.work.Level;
import com.test.test.sheetmusic.work.WorkStatus;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 곡 카드 (02 §2-2) — 검색·인기곡·작곡가 상세·같은 작곡가 곡. */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkSummaryDTO {

    private Long id;
    private String titleKo;
    private String titleOriginal;
    private ComposerRefDTO composer;
    private List<String> catalogNumbers;
    private Level level;
    private WorkStatus status;
    private Integer pageCount;
    private Long fileSize;
    private String previewUrl;
    /** 검색 응답에서만 값이 있다 (02 §3-1 6번). 그 외 항상 null. */
    private String matchedAlias;
}
