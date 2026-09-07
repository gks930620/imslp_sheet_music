package com.test.test.sheetmusic.crawl;

import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 작품 페이지 파싱 결과 (00 조사 §1). */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParsedWorkPage {

    private String canonicalUrl;
    private String titleOriginal;
    private String composerNameOriginal;
    private String composerImslpUrl;
    private List<String> catalogNumbers;
    private List<String> aliases;
    private String musicalKey;
    private String compositionYear;
    private String movements;
    private String instrumentation;
    private List<ParsedEdition> editions;

    public List<String> getCatalogNumbers() {
        return catalogNumbers == null ? new ArrayList<>() : catalogNumbers;
    }

    public List<String> getAliases() {
        return aliases == null ? new ArrayList<>() : aliases;
    }

    public List<ParsedEdition> getEditions() {
        return editions == null ? new ArrayList<>() : editions;
    }

    /**
     * 피아노 독주 판정 (02 §6-10): 위키텍스트/일반정보의 {@code Instrumentation} 이 정확히 "piano" 이거나
     * 카테고리에 {@code For piano}(편곡 {@code (arr)} 제외)가 있으면 피아노 독주로 본다.
     */
    public boolean isPianoSolo(List<String> categories) {
        if (instrumentation != null && instrumentation.trim().equalsIgnoreCase("piano")) {
            return true;
        }
        if (categories == null) {
            return false;
        }
        for (String category : categories) {
            String normalized = category.replace('_', ' ').trim();
            if (normalized.equalsIgnoreCase("For piano")) {
                return true;
            }
        }
        return false;
    }
}
