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

    /**
     * "받게 되는 악보가 찾은 것과 어떻게 다른가" (02 §2-2-1). <b>이 DTO 를 쓰는 모든 응답</b>에서 같은 규칙으로 채운다
     * — matchedAlias 처럼 한 응답에서만 채우는 값이 아니다. 할 말이 없으면 필드 자체가 null 이다.
     */
    private ScopeNoteDTO scopeNote;
}
