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
    /**
     * 수록곡 안내 (02 §4-7, 2026-09-08 계약 보완). §4-8 요청 DTO 에 있는 필드는 같은 리소스의 상세 응답에도 있어야 한다 —
     * PUT 이 전체 교체라 관리 화면이 이 응답으로 폼을 채워 되돌려 보내기 때문이다(없으면 저장 한 번에 값이 지워진다).
     */
    private String collectionGuide;
    private String imslpUrl;
    private boolean hidden;
    private HiddenReason hiddenReason;
    private WorkStatus status;
    private boolean needsWork;
    private List<WorkMissing> missing;
    private Long recommendedEditionId;
    private Long candidateEditionId;
    /** 지금 추천 판본이 사람 눈을 통과했는가 (02 §4-7 · §5-6-1, 2026-09-08 신설). */
    private boolean recommendationReviewed;
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
