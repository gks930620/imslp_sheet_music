package com.test.test.sheetmusic.work.dto;

import com.test.test.sheetmusic.edition.dto.EditionDTO;
import com.test.test.sheetmusic.work.Level;
import com.test.test.sheetmusic.work.WorkStatus;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 곡 상세 (02 §3-3). */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkDetailDTO {

    private Long id;
    private String titleKo;
    private String titleOriginal;
    private ComposerRefDTO composer;
    private List<String> catalogNumbers;
    private Level level;
    private WorkStatus status;
    private List<String> aliases;
    private String compositionYear;
    private String musicalKey;
    private String movements;
    private String movementPageGuide;
    /**
     * 수록곡 안내 (02 §3-3, 2026-09-08 추가) — 사람이 쓴 완성 문장을 그대로 내려준다(서버가 조립하지 않는다).
     * 이 값이 있는 곡이 묶음 악보이고, 검색 항목 scopeNote 의 COLLECTION 판정 근거다(§2-2-1).
     */
    private String collectionGuide;
    private String imslpUrl;
    private String composerImslpUrl;
    private EditionDTO recommendedEdition;
    private List<EditionDTO> otherEditions;
    /** 추천을 뺀 <b>전체</b> 판본 수 (02 §3-3). {@code otherEditions.size()} 보다 크면 목록이 잘린 것이다. */
    private int otherEditionsTotal;
    /** 전체 판본 중 downloadable=true 수 — 잘린 목록이 아니라 전체 기준이다 (02 §3-3). */
    private int downloadableOtherCount;
    private List<WorkSummaryDTO> sameComposerWorks;
}
