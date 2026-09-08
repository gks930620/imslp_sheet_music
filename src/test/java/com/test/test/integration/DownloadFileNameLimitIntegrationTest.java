package com.test.test.integration;

import com.test.test.integration.support.AdminApiTestSupport;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 다운로드 파일명 길이 상한 — 02_API_명세서 §3-4 (2026-09-08 추가, qa 3차 결함 6).
 *
 * <p><b>왜 상한이 필요한가.</b> 파일명은 {@code {작곡가} - {제목} ({작품번호}).pdf} 로 조립되는데
 * 세 조각의 상한(§0-6: 작곡가 100자 · 제목 300자 · 작품번호 100자)을 그대로 더하면 500자가 넘고,
 * 한글은 UTF-8 3바이트라 1,000바이트를 넘긴다. 그러면
 * <ul>
 *   <li><b>파일 시스템이 거부한다</b> — ext4 는 파일명 255<b>바이트</b>, NTFS·APFS 는 255<b>자</b>가 한계다.
 *       브라우저가 저장에 실패하거나 제멋대로 잘라 낸다(잘리는 규칙은 브라우저마다 다르다).</li>
 *   <li><b>헤더가 부푼다</b> — {@code filename*} 는 퍼센트 인코딩이라 한글 1자가 9바이트가 된다.</li>
 * </ul>
 * 실데이터 최장 제목이 41자라 지금은 아무도 다치지 않지만, 수집이 가져오는 원어 제목은 길다
 * (IMSLP 의 "Variations on a Theme by …" 계열). 상한이 없는 것과 넉넉한 상한이 있는 것은 다르다.
 *
 * <p><b>확정 계약(§3-4)</b> — 조립이 끝난 이름을 뒤에서 줄인다.
 * <ol>
 *   <li>확장자를 뺀 부분이 UTF-8 <b>200바이트</b>를 넘으면 <b>제목 부분만</b> 뒤에서 글자 단위로 줄인다.
 *       작곡가와 작품번호는 남긴다 — 곡을 식별하는 건 그 둘이다.</li>
 *   <li>제목을 다 없애도 넘으면 이름 전체를 200바이트 이하의 마지막 <b>문자 경계</b>에서 자른다
 *       (글자를 반토막 내면 깨진 파일명이 된다).</li>
 *   <li>말줄임표 같은 잘림 표시를 붙이지 않는다 — 파일명은 읽을 문장이 아니라 식별자이고,
 *       특수문자를 늘리면 금지문자 치환 규칙과 다시 부딪힌다.</li>
 * </ol>
 * 최종 파일명은 {@code .pdf} 포함 <b>204바이트 이하</b> = 255바이트 한계 안, 브라우저 중복 접미사
 * {@code " (1)"} 여유까지 남는다.
 */
class DownloadFileNameLimitIntegrationTest extends AdminApiTestSupport {

    /** §3-4 — 확장자 포함 최종 파일명의 UTF-8 바이트 상한. */
    private static final int MAX_FILENAME_BYTES = 204;
    /** 헤더 자체가 부풀지 않는지 — 퍼센트 인코딩(한글 1자 = 9바이트)까지 포함한 실측 상한. */
    private static final int MAX_CONTENT_DISPOSITION_BYTES = 1024;

    @Test
    @DisplayName("제목이 300자여도 파일명은 204바이트 이하 — 제목만 잘리고 작곡가·작품번호는 남는다")
    void longTitleIsTruncatedButComposerAndCatalogSurvive() throws Exception {
        Tokens admin = loginAdmin();
        String composerName = "긴제목작곡가";
        String catalog = "Op.27 No.2";
        String title = repeat("월광소나타긴제목", 300);

        long editionId = readyEdition(admin, composerName, "Longtitle, " + uniq(), title, catalog);
        String fileName = downloadFileName(editionId);

        assertThat(utf8Length(fileName))
                .as("파일명 UTF-8 바이트 (ext4 255바이트 한계 안, 브라우저 중복 접미사 여유 포함)")
                .isLessThanOrEqualTo(MAX_FILENAME_BYTES);
        assertThat(fileName).startsWith(composerName + " - ");
        assertThat(fileName)
                .as("작품번호는 곡을 식별하는 조각이라 잘라 내지 않는다")
                .endsWith(" (" + catalog + ").pdf");

        String truncatedTitle = fileName.substring((composerName + " - ").length(),
                fileName.length() - (" (" + catalog + ").pdf").length());
        assertThat(truncatedTitle).isNotEmpty();
        assertThat(title)
                .as("제목은 앞에서부터 남기고 뒤를 버린다 — 말줄임표 같은 표시를 덧붙이지 않는다")
                .startsWith(truncatedTitle);
    }

    @Test
    @DisplayName("작곡가 100자 + 제목 300자 + 작품번호 100자(전부 상한 최대)여도 204바이트 이하, 글자가 반토막 나지 않는다")
    void allFieldsAtTheirMaximumStillFitAndStayValidUtf8() throws Exception {
        Tokens admin = loginAdmin();
        String composerName = repeat("아주긴작곡가이름", 100);
        // 숫자를 넣지 않는다 — 정렬 파생값(sort_key) 팽창은 별개 결함이라 여기서 섞지 않는다
        // (AdminSaveLengthValidationIntegrationTest#work_catalogNumber_maxLength_andDerivedSortKeyDoesNotOverflow)
        String catalog = repeat("OPUS", 100);
        String title = repeat("아주긴곡제목", 300);

        long editionId = readyEdition(admin, composerName, "Maxlength, " + uniq(), title, catalog);
        String fileName = downloadFileName(editionId);

        assertThat(utf8Length(fileName)).isLessThanOrEqualTo(MAX_FILENAME_BYTES);
        assertThat(fileName).endsWith(".pdf");
        assertThat(fileName)
                .as("UTF-8 문자 중간에서 자르면 U+FFFD 가 섞인다 — 파일명이 깨진 것이다")
                .doesNotContain("�");
        assertThat(fileName.codePointCount(0, fileName.length()))
                .as("NTFS·APFS 는 255'자' 한계다 — 바이트 상한이 자 상한도 만족시킨다")
                .isLessThanOrEqualTo(255);
    }

    @Test
    @DisplayName("Content-Disposition 헤더 전체도 1KB 를 넘지 않는다 (filename* 퍼센트 인코딩 포함)")
    void contentDispositionHeaderDoesNotBloat() throws Exception {
        Tokens admin = loginAdmin();
        long editionId = readyEdition(admin, repeat("헤더작곡가", 100), "Headerbloat, " + uniq(),
                repeat("헤더제목", 300), repeat("OPUS", 100));

        MvcResult result = mockMvc.perform(get("/api/editions/{id}/download", editionId))
                .andExpect(status().isOk())
                .andReturn();
        String header = result.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION);

        assertThat(header).isNotNull();
        assertThat(utf8Length(header)).isLessThanOrEqualTo(MAX_CONTENT_DISPOSITION_BYTES);
        assertThat(header)
                .as("ASCII 대체 파일명(§3-4)은 그대로 — 길이 문제가 없다")
                .contains("filename=\"score-" + editionId + ".pdf\"");
    }

    // ===== 픽스처 =====

    /** 지정한 작곡가·제목·작품번호로 바로 받을 수 있는(READY) 판본 1개. */
    private long readyEdition(Tokens admin, String composerNameKo, String composerNameOriginal,
                              String titleKo, String catalog) throws Exception {
        long composerId = createComposer(admin, composerBody(composerNameKo, composerNameOriginal));
        Map<String, Object> work = workBody(composerId, titleKo, "Zz Long Name Piece " + uniq());
        work.put("catalogNumbers", List.of(catalog));
        long workId = createWork(admin, work);
        long editionId = createFileEdition(admin, workId, "FREE", "테스트 판정 근거");
        setRecommended(admin, workId, editionId);
        return editionId;
    }

    /** 응답의 {@code filename*=UTF-8''…} 를 디코딩한 실제 파일명. */
    private String downloadFileName(long editionId) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/editions/{id}/download", editionId))
                .andExpect(status().isOk())
                .andReturn();
        String header = result.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION);
        assertThat(header).as("Content-Disposition").isNotNull();

        int marker = header.indexOf("filename*=UTF-8''");
        assertThat(marker).as("filename* 가 있어야 한다 (§3-4): %s", header).isNotNegative();
        String encoded = header.substring(marker + "filename*=UTF-8''".length());
        int end = encoded.indexOf(';');
        return decodeRfc5987(end < 0 ? encoded : encoded.substring(0, end));
    }

    /** RFC 5987 ext-value 디코딩 — 퍼센트 시퀀스를 바이트로 모은 뒤 UTF-8 로 한 번에 읽는다. */
    private static String decodeRfc5987(String value) {
        byte[] bytes = new byte[value.length()];
        int size = 0;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '%' && i + 2 < value.length()) {
                bytes[size++] = (byte) Integer.parseInt(value.substring(i + 1, i + 3), 16);
                i += 2;
            } else {
                bytes[size++] = (byte) c;
            }
        }
        return new String(bytes, 0, size, StandardCharsets.UTF_8);
    }

    private static int utf8Length(String s) {
        return s.getBytes(StandardCharsets.UTF_8).length;
    }

    /** 정확히 {@code length} 자짜리 문자열 (§0-6 상한 최대치를 만든다). */
    private static String repeat(String unit, int length) {
        StringBuilder sb = new StringBuilder(length);
        while (sb.length() < length) {
            sb.append(unit);
        }
        return sb.substring(0, length);
    }
}
