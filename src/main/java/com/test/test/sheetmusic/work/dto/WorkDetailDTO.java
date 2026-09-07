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
    private String imslpUrl;
    private String composerImslpUrl;
    private EditionDTO recommendedEdition;
    private List<EditionDTO> otherEditions;
    private int downloadableOtherCount;
    private List<WorkSummaryDTO> sameComposerWorks;
}
