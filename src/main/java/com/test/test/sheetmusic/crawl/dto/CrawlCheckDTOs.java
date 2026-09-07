package com.test.test.sheetmusic.crawl.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 수집 주소 확인·작업 생성 DTO (02 §6-1, §6-2). */
public final class CrawlCheckDTOs {

    private CrawlCheckDTOs() {
    }

    /** §6-1 요청. */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CheckRequest {

        @NotEmpty(message = "확인할 주소를 입력해 주세요")
        @Size(max = 500, message = "한 번에 500개까지 확인할 수 있어요")
        private List<@Size(max = 500, message = "500자를 넘을 수 없어요") String> urls;

        /** 예상 시간 계산용. 기본 true. */
        private Boolean fetchFiles;

        public boolean fetchFilesOrDefault() {
            return fetchFiles == null || fetchFiles;
        }
    }

    /** §6-1 판정 결과 1건. */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CheckItem {
        private int seq;
        private String inputUrl;
        private String canonicalUrl;
        private String verdict;
        private Long existingWorkId;
        private Integer duplicateOfSeq;
    }

    /** §6-1 요약. */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CheckSummary {
        private int newCount;
        private int attachCount;
        private int existsCount;
        private int invalidCount;
        private int duplicateCount;
        private long estimatedSeconds;
    }

    /** §6-1 응답. */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CheckResult {
        private List<CheckItem> items;
        private CheckSummary summary;
    }

    /** §6-2 요청 항목. */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class JobItemRequest {

        @NotBlank(message = "수집할 주소를 입력해 주세요")
        @Size(max = 500, message = "500자를 넘을 수 없어요")
        private String url;

        private boolean refresh;
    }

    /** §6-2 요청. */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class JobRequest {

        @NotEmpty(message = "수집할 주소를 입력해 주세요")
        private List<@Valid JobItemRequest> items;

        private Boolean fetchFiles;

        public boolean fetchFilesOrDefault() {
            return fetchFiles == null || fetchFiles;
        }
    }
}
