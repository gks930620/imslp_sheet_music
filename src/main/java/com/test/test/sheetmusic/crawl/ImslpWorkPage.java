package com.test.test.sheetmusic.crawl;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * {@link ImslpClient#fetchWorkPage(String)} 의 결과 — 작품 페이지 HTML 원문. 파싱 전 순수 값.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImslpWorkPage {
    /** 요청에 쓴 정규 주소 */
    private String canonicalUrl;
    /** 페이지 HTML 전체 (UTF-8 디코딩·gzip 해제 완료) */
    private String html;
}
