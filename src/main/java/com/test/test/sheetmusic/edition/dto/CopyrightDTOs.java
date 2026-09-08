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

    /** §5-6 추천 판본 지정 요청·응답. */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecommendRequest {

        @NotNull(message = "판본을 선택해 주세요")
        private Long editionId;
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
        /**
         * 해당되는 경고가 <b>전부</b> 고정 순서로 온다. 없으면 빈 배열이고 <b>null 이 아니다</b> (02 §5-6, 2026-09-08).
         * 옛 단수 필드 {@code warning} 은 삭제했다 — 같은 뜻의 필드를 둘 두면 화면마다 다른 걸 읽는다.
         */
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
        private PageResponse<PendingEdition> editions;
    }
}
