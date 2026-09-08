package com.test.test.sheetmusic.common;

/**
 * 작품번호 정렬 키 (docs/설계/01_ERD.md §3-5 sort_key).
 * 표시 원문("Op.27 No.2")의 숫자 구간을 6자리 0-패딩하고 나머지는 {@link SearchNormalizer#normalize} 한다
 * → "op000027no000002". 문자열 정렬로 Op.9 &lt; Op.10 이 되게 한다.
 *
 * <p>숫자 구간은 <b>정규화 전 원문</b> 기준으로 나눈다 — "K.331/300i" 의 슬래시를 먼저 지우면
 * 331 과 300 이 한 덩어리가 되기 때문이다.
 */
public final class CatalogSortKey {

    private static final int PAD_WIDTH = 6;
    /**
     * {@code work_catalog_number.sort_key} 컬럼 폭 (01_ERD §3-5). 원문 100자의 최대 팽창은 350자
     * (1자리 숫자 50개 × 6자 패딩 + 구분 문자 50자)이고, 600 은 거기에 여유를 둔 값이다.
     */
    private static final int MAX_LENGTH = 600;

    private CatalogSortKey() {
    }

    /** @param catalogValue 표시 원문(정규화 전). null/blank → "" */
    public static String of(String catalogValue) {
        if (catalogValue == null || catalogValue.isBlank()) {
            return "";
        }
        StringBuilder key = new StringBuilder();
        int i = 0;
        int length = catalogValue.length();
        while (i < length) {
            int start = i;
            boolean digitRun = Character.isDigit(catalogValue.charAt(i));
            while (i < length && Character.isDigit(catalogValue.charAt(i)) == digitRun) {
                i++;
            }
            String chunk = catalogValue.substring(start, i);
            key.append(digitRun ? pad(chunk) : SearchNormalizer.normalize(chunk));
        }
        // 파생값이 저장을 깨뜨리면 안 된다 (01_ERD §3-5) — 넘치면 자른다.
        // 잘린 키는 그 뒤 구간의 정렬 순서가 뭉개질 뿐이고, 원문(catalog_value)은 그대로 남는다.
        return key.length() <= MAX_LENGTH ? key.toString() : key.substring(0, MAX_LENGTH);
    }

    /** 6자리 0-패딩. 6자리를 넘는 숫자는 자르지 않고 그대로 둔다. */
    private static String pad(String digits) {
        if (digits.length() >= PAD_WIDTH) {
            return digits;
        }
        return "0".repeat(PAD_WIDTH - digits.length()) + digits;
    }
}
