package com.test.test.sheetmusic.crawl;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * {@link ImslpClient#fetchWikitext(String)} 의 결과 — 위키텍스트 원문 + 카테고리 이름 목록.
 * 카테고리는 api.php 가 주는 형태 그대로(공백 대신 밑줄, 예: {@code For_piano}, {@code Scores_featuring_the_piano}).
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImslpWikitextPage {
    private String canonicalUrl;
    private String wikitext;
    private List<String> categories;
}
