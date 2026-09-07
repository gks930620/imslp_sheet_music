package com.test.test.sheetmusic.common;

import java.util.regex.Pattern;

/**
 * 다운로드 파일명 규칙 (docs/설계/02_API_명세서.md §3-4, 기획 01 §9 8-9).
 * {@code {작곡가 한글 또는 원어} - {한국어 제목 또는 원어 제목}{ (대표 작품번호)}.pdf}
 */
public final class DownloadFileName {

    private static final Pattern FORBIDDEN = Pattern.compile("[\\\\/:*?\"<>|]");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private DownloadFileName() {
    }

    /**
     * @param composerNameKo       작곡가 한글 표기 (null/blank 면 원어 표기로 폴백)
     * @param composerNameOriginal 작곡가 원어 표기
     * @param titleKo              한국어 대표 제목 (null/blank 면 원어 제목으로 폴백)
     * @param titleOriginal        원어 제목
     * @param primaryCatalogNumber 대표 작품번호(sort_order 0). null/blank 면 괄호째 생략
     * @return 확장자 .pdf 포함. 금지 문자 {@code \ / : * ? " < > |} → '-', 연속 공백 → 하나, 앞뒤 공백 제거
     */
    public static String build(String composerNameKo, String composerNameOriginal,
                               String titleKo, String titleOriginal,
                               String primaryCatalogNumber) {
        String composer = clean(pick(composerNameKo, composerNameOriginal));
        String title = clean(pick(titleKo, titleOriginal));
        String catalog = clean(primaryCatalogNumber);

        StringBuilder name = new StringBuilder();
        name.append(composer).append(" - ").append(title);
        if (!catalog.isEmpty()) {
            name.append(" (").append(catalog).append(")");
        }
        return name.append(".pdf").toString();
    }

    private static String pick(String preferred, String fallback) {
        return (preferred == null || preferred.isBlank()) ? fallback : preferred;
    }

    private static String clean(String value) {
        if (value == null) {
            return "";
        }
        String replaced = FORBIDDEN.matcher(value).replaceAll("-");
        return WHITESPACE.matcher(replaced).replaceAll(" ").trim();
    }
}
