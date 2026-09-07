package com.test.test.sheetmusic.common;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

/**
 * IMSLP 작품 페이지 주소 정규화 (02_API_명세서 §6-1).
 *
 * <p>정규 형태 = {@code https://imslp.org/wiki/} + (퍼센트 디코딩한 제목, 공백 → {@code _}, {@code #}/{@code ?} 이후 제거).
 * 비ASCII·아포스트로피·쉼표·슬래시·괄호는 그대로 둔다 — {@code Für_Elise…} 와 {@code F%C3%BCr_Elise…} 는 같은 주소다.
 *
 * <p>{@code java.net.URI} 를 쓰지 않는다 — 디코딩된 비ASCII 주소({@code Für_Elise})는 URI 문법 위반이라 파싱에 실패한다.
 */
public final class ImslpUrlNormalizer {

    public static final String WIKI_PREFIX = "https://imslp.org/wiki/";
    private static final String SITE_PREFIX = "https://imslp.org/";
    private static final String WIKI_PATH = "/wiki/";
    private static final List<String> ALLOWED_SCHEMES = List.of("http", "https");
    private static final List<String> ALLOWED_HOSTS = List.of("imslp.org", "www.imslp.org");
    private static final List<String> EXCLUDED_TITLE_PREFIXES =
            List.of("special:", "category:", "file:", "image:", "template:");

    private ImslpUrlNormalizer() {
    }

    /**
     * 작품 페이지 주소를 정규 형태로. 작품 페이지가 아니면 {@code null}(호출자가 INVALID_URL / 400 으로 처리).
     */
    public static String canonicalize(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            return null;
        }
        String url = rawUrl.trim();
        int schemeEnd = url.indexOf("://");
        if (schemeEnd <= 0) {
            return null;
        }
        String scheme = url.substring(0, schemeEnd).toLowerCase(Locale.ROOT);
        if (!ALLOWED_SCHEMES.contains(scheme)) {
            return null;
        }
        String rest = url.substring(schemeEnd + 3);
        int pathStart = rest.indexOf('/');
        if (pathStart < 0) {
            return null;
        }
        String host = rest.substring(0, pathStart).toLowerCase(Locale.ROOT);
        if (!ALLOWED_HOSTS.contains(host)) {
            return null;
        }
        String path = rest.substring(pathStart);
        if (!path.startsWith(WIKI_PATH)) {
            return null;
        }
        String rawTitle = path.substring(WIKI_PATH.length());
        int hash = rawTitle.indexOf('#');
        if (hash >= 0) {
            rawTitle = rawTitle.substring(0, hash);
        }
        int question = rawTitle.indexOf('?');
        if (question >= 0) {
            rawTitle = rawTitle.substring(0, question);
        }
        if (rawTitle.isBlank()) {
            return null;
        }
        String title = decode(rawTitle).replace(' ', '_').trim();
        if (title.isEmpty()) {
            return null;
        }
        String lower = title.toLowerCase(Locale.ROOT);
        for (String prefix : EXCLUDED_TITLE_PREFIXES) {
            if (lower.startsWith(prefix)) {
                return null;
            }
        }
        return WIKI_PREFIX + title;
    }

    /** 정규 주소에서 MediaWiki 페이지 제목(밑줄 유지)을 뽑는다. */
    public static String titleOf(String canonicalUrl) {
        if (canonicalUrl == null || !canonicalUrl.startsWith(WIKI_PREFIX)) {
            return null;
        }
        return canonicalUrl.substring(WIKI_PREFIX.length());
    }

    /** 작곡가 카테고리·파일 페이지 등 "IMSLP 주소인가"만 보는 느슨한 검사 (02 §4-4·§5-2 검증). */
    public static boolean isImslpUrl(String url) {
        return url != null && url.startsWith(SITE_PREFIX);
    }

    /** 작곡가 카테고리 주소 (01_ERD §6 시드 로더 규칙). */
    public static String composerCategoryUrl(String nameOriginal) {
        if (nameOriginal == null || nameOriginal.isBlank()) {
            return null;
        }
        return SITE_PREFIX + "wiki/Category:" + nameOriginal.trim().replace(' ', '_');
    }

    /** 퍼센트 디코딩. {@code +} 는 공백이 아니라 그대로 둔다(제목에 쓰일 수 있음). */
    private static String decode(String value) {
        try {
            return URLDecoder.decode(value.replace("+", "%2B"), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return value;
        }
    }
}
