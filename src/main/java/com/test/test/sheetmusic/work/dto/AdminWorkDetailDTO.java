package com.test.test.sheetmusic.work.dto;

import com.test.test.sheetmusic.composer.ComposerEntity;
import com.test.test.sheetmusic.edition.dto.AdminEditionDTO;
import com.test.test.sheetmusic.work.HiddenReason;
import com.test.test.sheetmusic.work.Level;
import com.test.test.sheetmusic.work.WorkMissing;
import com.test.test.sheetmusic.work.WorkStatus;
import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 곡 상세(관리) (02 §4-7). */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminWorkDetailDTO {

    private Long id;
    private String titleKo;
    private String titleOriginal;
    private Composer composer;
    private List<String> catalogNumbers;
    private List<String> aliases;
    private Level level;
    private String compositionYear;
    private String musicalKey;
    private String movements;
    private String movementPageGuide;
    private String imslpUrl;
    private boolean hidden;
    private HiddenReason hiddenReason;
    private WorkStatus status;
    private boolean needsWork;
    private List<WorkMissing> missing;
    private Long recommendedEditionId;
    private Long candidateEditionId;
    private long downloadCount;
    private boolean hasDownloadHistory;
    private List<AdminEditionDTO> editions;
    private Instant createdAt;
    private Instant updatedAt;

    /** 관리 화면의 작곡가 요약 — 저작권 판정 근거(몰년)와 보완 표시를 포함한다. */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Composer {
        private Long id;
        private String nameKo;
        private String nameOriginal;
        private Integer deathYear;
        private boolean nameKoMissing;

        public static Composer from(ComposerEntity composer) {
            return Composer.builder()
                    .id(composer.getId())
                    .nameKo(composer.getNameKo())
                    .nameOriginal(composer.getNameOriginal())
                    .deathYear(composer.getDeathYear())
                    .nameKoMissing(composer.getNameKo() == null || composer.getNameKo().isBlank())
                    .build();
        }
    }
}
