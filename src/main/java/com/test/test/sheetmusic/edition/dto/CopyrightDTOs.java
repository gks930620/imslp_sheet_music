package com.test.test.sheetmusic.edition.dto;

import com.test.test.common.dto.PageResponse;
import com.test.test.sheetmusic.edition.EditionKind;
import com.test.test.sheetmusic.edition.EditionScope;
import com.test.test.sheetmusic.edition.FileFetchStatus;
import com.test.test.sheetmusic.edition.KoreaCopyright;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 저작권 판정·추천 지정·파일 받아오기 관련 DTO 모음 (02 §5-6 ~ §5-10). */
public final class CopyrightDTOs {

    private CopyrightDTOs() {
    }

    /** §5-9 단건 판정 요청. */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class JudgeRequest {

        @NotNull(message = "저작권 판정을 선택해 주세요")
        private KoreaCopyright koreaCopyright;

        @Size(max = 1000, message = "1000자를 넘을 수 없어요")
        private String copyrightNote;
    }

    /** §5-10 일괄 판정 요청. */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BulkJudgeRequest {

        @NotEmpty(message = "판정할 판본을 선택해 주세요")
        private List<Long> editionIds;

        @NotNull(message = "저작권 판정을 선택해 주세요")
        private KoreaCopyright koreaCopyright;

        @Size(max = 1000, message = "1000자를 넘을 수 없어요")
        private String copyrightNote;
    }

    /** §5-10 응답. */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BulkJudgeResult {
        private List<Long> succeeded;
        private List<Failure> failed;

        @Getter
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class Failure {
            private Long editionId;
            private String reason;
        }
    }

    /**
     * §5-6 추천 판본 지정 요청·응답 (2026-09-21 전면 개정 — 기획 06 §1-4·§3-1·§3-2·§3-3).
     *
     * <p>{@code reason}·{@code note} 는 일부러 <b>문자열</b>로 받는다(enum 타입이 아니다) — 모르는 값을
     * Jackson 이 역직렬화 단계에서 튕기면 404(존재 확인)보다 먼저 400 이 나가 "검증 순서는 404 → 400" 계약이
     * 깨진다. 그래서 존재를 먼저 확인한 뒤 서비스가 직접 파싱해 필드 오류로 돌려준다.
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecommendRequest {

        @NotNull(message = "판본을 선택해 주세요")
        private Long editionId;

        /** {@code RecommendationReason} 6개 중 하나 — 필수. 누락·모르는 값은 서비스가 400 field {@code reason}. */
        private String reason;

        /** {@code reason = OTHER} 면 필수, 아니면 선택. 공백만 있으면 서비스가 null 로 정규화한다. */
        private String note;

        /** 기본 false — {@code true} 면 지정과 동시에 검수를 끝낸다(§5-6-1 을 따로 부르지 않는다). */
        private Boolean reviewed;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecommendResult {
        private Long workId;
        private Long previousEditionId;
        private Long editionId;
        private com.test.test.sheetmusic.work.WorkStatus workStatus;

        /** 요청의 {@code reviewed} 가 그대로 반영된 결과 (2026-09-21 신설). */
        private boolean recommendationReviewed;

        /**
         * 해당되는 경고가 <b>전부</b> 고정 순서로 온다. 없으면 빈 배열이고 <b>null 이 아니다</b> (02 §5-6, 2026-09-08).
         * 옛 단수 필드 {@code warning} 은 삭제했다 — 같은 뜻의 필드를 둘 두면 화면마다 다른 걸 읽는다.
         * 2026-09-21 — 3종에서 6종으로, <b>지정 직전 상태</b>로 계산한다(§5-6-2 와 같은 함수).
         */
        private java.util.List<com.test.test.sheetmusic.edition.RecommendWarning> warnings;
    }

    /** §5-6-2 바꾸기 전 경고 예고 (2026-09-21 신설) — §5-6 응답과 <b>같은 함수</b>로 계산한다. */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecommendPreviewResult {
        private Long workId;
        private Long editionId;
        private java.util.List<com.test.test.sheetmusic.edition.RecommendWarning> warnings;
    }

    /** §5-7 파일 받아오기 응답. */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FetchFileResult {
        private Long editionId;
        private FileFetchStatus fileFetchStatus;
    }

    /** §5-8 대기함 행. */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PendingEdition {
        private Long editionId;
        private Work work;
        private Composer composer;
        private EditionKind kind;
        private EditionScope scope;
        private Integer movementNumber;
        private String editor;
        private String arranger;
        private String publisher;
        private Integer publishYear;
        private String imslpCopyrightText;
        private String imslpFileUrl;
        private boolean hasFile;

        /**
         * 지금 §5-11 자동 판정을 돌리면 이 판본이 <b>왜 자동으로 열리지 않는지</b>.
         * 자동으로 FREE 가 될 수 있으면 {@code null}. 관리자가 남은 일감의 종류를 목록에서 바로 보는 값이다.
         * 계산만 하고 아무것도 바꾸지 않는다.
         */
        private com.test.test.sheetmusic.edition.CopyrightAutoJudge.SkipReason autoJudgeSkipReason;

        @Getter
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class Work {
            private Long id;
            private String titleKo;
            private String titleOriginal;
        }

        @Getter
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class Composer {
            private Long id;
            private String nameKo;
            private String nameOriginal;
            private Integer deathYear;
        }
    }

    /** §5-8 응답 래퍼. */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PendingListResult {
        private long unfilteredTotal;

        /**
         * 지금 §5-12 로 되돌릴 수 있는 잔량 (§5-8-1). <b>항상 있다</b> — 없으면 0/0 이다.
         * 필터·페이지와 무관한 화면 전체의 상태라 {@code unfilteredTotal} 과 같은 자리에 둔다.
         */
        private AutoJudgeDTOs.RevertibleSummary autoJudged;

        private PageResponse<PendingEdition> editions;
    }
}
