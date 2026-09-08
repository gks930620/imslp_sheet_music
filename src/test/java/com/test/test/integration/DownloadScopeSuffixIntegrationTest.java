package com.test.test.integration;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 다운로드 파일명의 범위·편곡 접미사 — 02 §3-4 (기획 01 §11-2 ③).
 *
 * <p>파일은 사용자 컴퓨터에 남아 몇 주 뒤 레슨 직전에 열린다. 그때 화면은 없고 파일 이름만 있으므로,
 * 추천 판본이 전곡·전체 악보가 아니면 그 사실이 이름에 남아야 한다.
 *
 * <p>{@code scope=MOVEMENT + movementNumber=null} 인 "발췌" 는 관리 API 로 만들 수 없어(§5-2 가 400 으로 막는다)
 * 여기서 검증하지 않는다 — 수집이 헤딩을 못 읽은 판본에서만 생긴다.
 */
class DownloadScopeSuffixIntegrationTest extends SheetMusicFixtureSupport {

    @Test
    @DisplayName("편곡이면 이름 끝에 ' 편곡'")
    void arrangement_appendsArrangementSuffix() throws Exception {
        Tokens admin = loginAdmin();
        long composerId = createComposer(admin, "리스트테스트", "Lisztscope, Zz");
        long editionId = recommendedEdition(admin, composerId, "zz사랑의 꿈", "Zz Liebestraume", "S.541",
                Map.of("kind", "ARRANGEMENT", "arranger", "Franz Liszt"));

        expectFileName(editionId, "리스트테스트 - zz사랑의 꿈 (S.541) 편곡.pdf");
    }

    @Test
    @DisplayName("특정 악장이면 이름 끝에 ' N악장'")
    void movement_appendsMovementSuffix() throws Exception {
        Tokens admin = loginAdmin();
        long composerId = createComposer(admin, "베토벤테스트", "Beethovenscope, Zz");
        long editionId = recommendedEdition(admin, composerId, "zz비창 소나타", "Zz Pathetique", "Op.13",
                Map.of("scope", "MOVEMENT", "movementNumber", 2));

        expectFileName(editionId, "베토벤테스트 - zz비창 소나타 (Op.13) 2악장.pdf");
    }

    @Test
    @DisplayName("둘 다면 편곡이 먼저, 악장이 뒤")
    void arrangementAndMovement_appendBothInOrder() throws Exception {
        Tokens admin = loginAdmin();
        long composerId = createComposer(admin, "둘다테스트", "Bothscope, Zz");
        long editionId = recommendedEdition(admin, composerId, "zz둘다 소나타", "Zz Both Sonata", "Op.27 No.2",
                Map.of("kind", "ARRANGEMENT", "scope", "MOVEMENT", "movementNumber", 2));

        expectFileName(editionId, "둘다테스트 - zz둘다 소나타 (Op.27 No.2) 편곡 2악장.pdf");
    }

    @Test
    @DisplayName("전체 악보 · 전곡이면 아무것도 붙지 않는다 (기존 이름 그대로)")
    void completeScore_hasNoSuffix() throws Exception {
        Tokens admin = loginAdmin();
        long composerId = createComposer(admin, "전곡테스트", "Completescope, Zz");
        long editionId = recommendedEdition(admin, composerId, "zz월광 소나타", "Zz Moonlight", "Op.27 No.2", Map.of());

        expectFileName(editionId, "전곡테스트 - zz월광 소나타 (Op.27 No.2).pdf");
        mockMvc.perform(get("/api/editions/{id}/download", editionId))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, not(containsString(rfc5987("편곡")))))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, not(containsString(rfc5987("악장")))));
    }

    @Test
    @DisplayName("상한(204바이트)에 걸리면 제목이 먼저 잘리고 접미사는 남는다")
    void suffixSurvivesTruncation() throws Exception {
        Tokens admin = loginAdmin();
        long composerId = createComposer(admin, "긴제목테스트", "Longtitlescope, Zz");
        String longTitle = "zz" + "가".repeat(120);   // 한글 3바이트 × 120 = 360바이트
        long editionId = recommendedEdition(admin, composerId, longTitle, "Zz Long Title", "Op.1",
                Map.of("kind", "ARRANGEMENT", "scope", "MOVEMENT", "movementNumber", 2));

        mockMvc.perform(get("/api/editions/{id}/download", editionId))
                .andExpect(status().isOk())
                // 작곡가·작품번호·접미사는 남는다
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        containsString(rfc5987("(Op.1) 편곡 2악장.pdf"))))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        containsString(rfc5987("긴제목테스트 - zz"))));

        // 확장자 포함 204바이트 이하
        String disposition = mockMvc.perform(get("/api/editions/{id}/download", editionId))
                .andReturn().getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION);
        org.assertj.core.api.Assertions.assertThat(decodedFileNameLength(disposition)).isLessThanOrEqualTo(204);
    }

    // ===== 헬퍼 =====

    private long recommendedEdition(Tokens admin, long composerId, String titleKo, String titleOriginal,
                                    String catalogNumber, Map<String, Object> overrides) throws Exception {
        long workId = createWork(admin, composerId, titleKo, titleOriginal,
                List.of(catalogNumber), List.of(), "INTERMEDIATE", false);
        long editionId = createEdition(admin, workId, uploadSamplePdf(admin), 5, "FREE", overrides);
        recommendEdition(admin, workId, editionId);
        return editionId;
    }

    private void expectFileName(long editionId, String expected) throws Exception {
        mockMvc.perform(get("/api/editions/{id}/download", editionId))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        containsString("filename*=UTF-8''" + rfc5987(expected))));
    }

    /** filename* 의 퍼센트 인코딩을 되돌린 UTF-8 바이트 길이. */
    private int decodedFileNameLength(String disposition) {
        int start = disposition.indexOf("filename*=UTF-8''");
        if (start < 0) {
            throw new AssertionError("filename* 가 없다: " + disposition);
        }
        String encoded = disposition.substring(start + "filename*=UTF-8''".length());
        int bytes = 0;
        for (int i = 0; i < encoded.length(); ) {
            if (encoded.charAt(i) == '%') {
                i += 3;
            } else {
                i += 1;
            }
            bytes++;
        }
        return bytes;
    }

    /** RFC 5987 ext-value 인코딩 (DownloadApiIntegrationTest 와 같은 규칙). */
    private static String rfc5987(String value) {
        StringBuilder sb = new StringBuilder();
        for (byte b : value.getBytes(StandardCharsets.UTF_8)) {
            int c = b & 0xff;
            boolean attrChar = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')
                    || "!#$&+-.^_`|~".indexOf(c) >= 0;
            if (attrChar) {
                sb.append((char) c);
            } else {
                sb.append('%').append(String.format("%02X", c));
            }
        }
        return sb.toString();
    }
}
