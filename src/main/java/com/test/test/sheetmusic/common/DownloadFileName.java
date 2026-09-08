package com.test.test.sheetmusic.common;

import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/**
 * 다운로드 파일명 규칙 (docs/설계/02_API_명세서.md §3-4, 기획 01 §9 8-9).
 * {@code {작곡가 한글 또는 원어} - {한국어 제목 또는 원어 제목}{ (대표 작품번호)}.pdf}
 */
public final class DownloadFileName {

    private static final Pattern FORBIDDEN = Pattern.compile("[\\\\/:*?\"<>|]");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final String EXTENSION = ".pdf";
    /** 확장자를 뺀 이름의 UTF-8 바이트 상한 (02 §3-4). {@code .pdf} 4바이트를 더해 204바이트. */
    private static final int MAX_BASE_BYTES = 200;

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
        return build(composerNameKo, composerNameOriginal, titleKo, titleOriginal, primaryCatalogNumber, null);
    }

    /**
     * 범위·편곡 접미사까지 붙인 이름 (02 §3-4, 2026-09-08 추가).
     *
     * <p>추천 판본이 전곡·전체 악보가 아니면 그 사실을 이름 끝에 남긴다 — 파일은 사용자 컴퓨터에 남아
     * 몇 주 뒤에 열리고, 그때 화면은 없고 파일 이름만 있다. 접미사는 우리가 만드는 문자열이라
     * 금지문자 치환 대상이 없다(치환·공백 정리 <b>뒤</b>에 붙인다).
     *
     * <p>길이 상한에서는 <b>작곡가·작품번호와 같은 등급</b>이다 — 제목이 먼저 잘리고 접미사는 남는다.
     * 접미사는 안내이기 이전에 "이 파일이 무엇인지" 의 일부다.
     *
     * @param scopeSuffix {@code EditionEntity.downloadNameSuffix()} 의 결과(예: {@code " 편곡 2악장"}). null/빈 문자열이면 안 붙는다
     */
    public static String build(String composerNameKo, String composerNameOriginal,
                               String titleKo, String titleOriginal,
                               String primaryCatalogNumber, String scopeSuffix) {
        String composer = clean(pick(composerNameKo, composerNameOriginal));
        String title = clean(pick(titleKo, titleOriginal));
        String catalog = clean(primaryCatalogNumber);

        String suffix = (catalog.isEmpty() ? "" : " (" + catalog + ")")
                + (scopeSuffix == null ? "" : scopeSuffix);
        String prefix = composer + " - ";
        return limit(prefix, title, suffix) + EXTENSION;
    }

    /**
     * 확장자를 뺀 이름을 UTF-8 {@value #MAX_BASE_BYTES} 바이트 안으로 줄인다 (02 §3-4, 2026-09-08 추가).
     *
     * <p>세 조각의 입력 상한(§0-6: 작곡가 100자 · 제목 300자 · 작품번호 100자)을 그대로 더하면 500자가 넘고
     * 한글은 UTF-8 3바이트라 1,000바이트를 넘는다. 그러면 ext4(255바이트)·NTFS/APFS(255자) 한계에 걸려
     * 브라우저가 저장에 실패하거나 제멋대로 잘라 낸다.
     *
     * <ol>
     *   <li><b>제목만</b> 뒤에서 글자 단위로 줄인다 — 곡을 식별하는 건 작곡가와 작품번호다.</li>
     *   <li>제목을 다 없애도 넘으면 이름 전체를 마지막 <b>문자 경계</b>에서 자른다(글자를 반토막 내면
     *       U+FFFD 가 섞인 깨진 파일명이 된다).</li>
     *   <li>말줄임표 같은 잘림 표시는 붙이지 않는다 — 파일명은 읽을 문장이 아니라 식별자이고,
     *       특수문자를 늘리면 금지문자 치환 규칙과 다시 부딪힌다.</li>
     * </ol>
     */
    private static String limit(String prefix, String title, String suffix) {
        String full = prefix + title + suffix;
        if (utf8Length(full) <= MAX_BASE_BYTES) {
            return full;
        }
        int fixed = utf8Length(prefix) + utf8Length(suffix);
        String shortTitle = truncateToBytes(title, MAX_BASE_BYTES - fixed);
        String shortened = prefix + shortTitle + suffix;
        return utf8Length(shortened) <= MAX_BASE_BYTES
                ? shortened
                : truncateToBytes(shortened, MAX_BASE_BYTES);
    }

    /** UTF-8 {@code maxBytes} 이하가 되도록 뒤에서 자른다 — 코드포인트(서로게이트 쌍 포함)를 쪼개지 않는다. */
    private static String truncateToBytes(String value, int maxBytes) {
        if (maxBytes <= 0) {
            return "";
        }
        int bytes = 0;
        int end = 0;
        while (end < value.length()) {
            int codePoint = value.codePointAt(end);
            int charCount = Character.charCount(codePoint);
            int size = utf8Length(value.substring(end, end + charCount));
            if (bytes + size > maxBytes) {
                break;
            }
            bytes += size;
            end += charCount;
        }
        return value.substring(0, end);
    }

    private static int utf8Length(String value) {
        return value.getBytes(StandardCharsets.UTF_8).length;
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
