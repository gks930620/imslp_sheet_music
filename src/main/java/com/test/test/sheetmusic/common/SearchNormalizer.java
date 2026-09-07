package com.test.test.sheetmusic.common;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 검색 정규화 (docs/설계/01_ERD.md §2). 저장 시와 검색 시 같은 함수를 쓴다.
 */
public final class SearchNormalizer {

    /** NFD 로 분해되지 않는 문자 치환표 (01_ERD §2 4번). */
    private static final String[][] SUBSTITUTIONS = {
            {"ß", "ss"}, {"ẞ", "ss"},
            {"ø", "o"}, {"Ø", "o"},
            {"ł", "l"}, {"Ł", "l"},
            {"æ", "ae"}, {"Æ", "ae"},
            {"œ", "oe"}, {"Œ", "oe"},
            {"đ", "d"}, {"Đ", "d"},
            {"ð", "d"}, {"Ð", "d"},
            {"þ", "th"}, {"Þ", "th"},
            {"ı", "i"}, {"İ", "i"},
    };

    private static final Pattern COMBINING_MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^\\p{L}\\p{N}]+");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private SearchNormalizer() {
    }

    /**
     * 01_ERD §2 규칙: NFD 분해 → 결합문자 제거 → NFC → 치환표(ß→ss …) → 소문자 → 문자·숫자 이외 제거.
     * null/blank → "".
     */
    public static String normalize(String s) {
        if (s == null || s.isBlank()) {
            return "";
        }
        String decomposed = Normalizer.normalize(s, Normalizer.Form.NFD);
        String stripped = COMBINING_MARKS.matcher(decomposed).replaceAll("");
        String recomposed = Normalizer.normalize(stripped, Normalizer.Form.NFC);
        String substituted = applySubstitutions(recomposed);
        String lower = substituted.toLowerCase(Locale.ROOT);
        return NON_ALPHANUMERIC.matcher(lower).replaceAll("");
    }

    /**
     * 검색어를 공백으로 나눠 단어마다 {@link #normalize} 한 목록. 빈 단어는 제거. null/blank → 빈 목록.
     */
    public static List<String> normalizeWords(String q) {
        List<String> words = new ArrayList<>();
        if (q == null || q.isBlank()) {
            return words;
        }
        for (String raw : WHITESPACE.split(q.trim())) {
            String normalized = normalize(raw);
            if (!normalized.isEmpty()) {
                words.add(normalized);
            }
        }
        return words;
    }

    private static String applySubstitutions(String s) {
        String result = s;
        for (String[] pair : SUBSTITUTIONS) {
            if (result.indexOf(pair[0].charAt(0)) >= 0) {
                result = result.replace(pair[0], pair[1]);
            }
        }
        return result;
    }
}
