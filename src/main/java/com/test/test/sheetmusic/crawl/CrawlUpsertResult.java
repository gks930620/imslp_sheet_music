package com.test.test.sheetmusic.crawl;

import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 수집 항목 1건의 저장 결과 — 워커가 파일 수신 대상을 고르는 데 쓴다. */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CrawlUpsertResult {

    private Long workId;
    private int editionCount;
    private boolean hidden;
    /** 파일을 받을 판본 (IMSLP 다운로드 수 상위 2개, 이미 파일이 있으면 제외). */
    private List<FetchTarget> fetchTargets;

    public List<FetchTarget> getFetchTargets() {
        return fetchTargets == null ? new ArrayList<>() : fetchTargets;
    }

    /** 파일 수신 대상 판본. */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FetchTarget {
        private Long editionId;
        private String imslpFileId;
    }
}
