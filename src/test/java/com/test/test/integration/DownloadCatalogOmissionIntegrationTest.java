package com.test.test.integration;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 다운로드 파일명의 작품번호 괄호 생략 — 02 §3-4 (기획 01 §12-1).
 *
 * <p>"괄호는 제목이 말하지 않은 것만 말한다." 생략 3조건을 <b>실제 저장된 곡</b>으로 왕복 검증한다.
 * ①(번호 없음)·②(제목에 이미 있음)는 {@code DownloadFileNameTest} 가 조합을 촘촘히 보고,
 * 여기서는 <b>③(작품번호가 여러 개)</b> 처럼 값 하나로는 알 수 없어 <b>호출자</b>가 판정해야 하는 것과,
 * 세 조건이 실제 응답 헤더까지 살아 나오는지를 본다.
 *
 * <p>qa 가 "다운로드가 열린 곡 전부"를 확인하는 항목이라(기획 §12-3) 시드 실데이터와 같은 값을 쓴다.
 */
class DownloadCatalogOmissionIntegrationTest extends SheetMusicFixtureSupport {

    @Test
    @DisplayName("③ 작품번호가 2개 이상이면 괄호째 생략 — 하나만 골라 적으면 '그 번호만 든 악보'로 읽힌다")
    void multipleCatalogNumbers_omitsBracket() throws Exception {
        Tokens admin = loginAdmin();
        long composerId = createComposer(admin, "멘델스존테스트", "Mendelssohnomit, Zz");
        long editionId = recommendedEdition(admin, composerId, "zz무언가 전곡", "Zz Lieder ohne Worte",
                List.of("Op.19b", "Op.30", "Op.38"), Map.of());

        expectFileName(editionId, "멘델스존테스트 - zz무언가 전곡.pdf");
    }

    @Test
    @DisplayName("③ 대조군: 작품번호가 1개면 괄호는 그대로 (같은 제목·같은 첫 번호)")
    void singleCatalogNumber_keepsBracket() throws Exception {
        Tokens admin = loginAdmin();
        long composerId = createComposer(admin, "대조군테스트", "Controlomit, Zz");
        long editionId = recommendedEdition(admin, composerId, "zz무언가 전곡", "Zz Lieder ohne Worte",
                List.of("Op.19b"), Map.of());

        expectFileName(editionId, "대조군테스트 - zz무언가 전곡 (Op.19b).pdf");
    }

    @Test
    @DisplayName("③ 작품번호가 2개면 서로 같은 곡의 다른 번호(D.899·Op.90)여도 생략한다")
    void twoCatalogNumbersOfSameWork_omitsBracket() throws Exception {
        Tokens admin = loginAdmin();
        long composerId = createComposer(admin, "슈베르트테스트", "Schubertomit, Zz");
        long editionId = recommendedEdition(admin, composerId, "zz즉흥곡 모음", "Zz 4 Impromptus",
                List.of("D.899", "Op.90"), Map.of());

        expectFileName(editionId, "슈베르트테스트 - zz즉흥곡 모음.pdf");
    }

    @Test
    @DisplayName("③ 대표 작품번호 값 안에 또 괄호가 있으면 생략")
    void catalogNumberWithParenthesis_omitsBracket() throws Exception {
        Tokens admin = loginAdmin();
        long composerId = createComposer(admin, "바흐테스트", "Bachomit, Zz");
        long editionId = recommendedEdition(admin, composerId, "zz안나 막달레나 수첩", "Zz Notebooks",
                List.of("BWV Anh.113–132 (1725년 수첩)"), Map.of());

        expectFileName(editionId, "바흐테스트 - zz안나 막달레나 수첩.pdf");
    }

    @Test
    @DisplayName("② 제목에 작품번호가 이미 들어 있으면 생략 — 쇼팽 - zz녹턴 Op.9.pdf")
    void titleAlreadyContainsCatalog_omitsBracket() throws Exception {
        Tokens admin = loginAdmin();
        long composerId = createComposer(admin, "쇼팽테스트", "Chopinomit, Zz");
        long editionId = recommendedEdition(admin, composerId, "zz녹턴 Op.9", "Zz Nocturnes, Op.9",
                List.of("Op.9"), Map.of());

        expectFileName(editionId, "쇼팽테스트 - zz녹턴 Op.9.pdf");
    }

    @Test
    @DisplayName("② 한국어 제목이 없으면 원어 제목으로 비교한다 (파일명에 실제로 쓰인 제목)")
    void originalTitleFallback_isTheComparisonTarget() throws Exception {
        Tokens admin = loginAdmin();
        long composerId = createComposer(admin, "폴백테스트", "Fallbackomit, Zz");
        long editionId = recommendedEdition(admin, composerId, null, "Zz Nocturnes, Op.9",
                List.of("Op.9"), Map.of());

        expectFileName(editionId, "폴백테스트 - Zz Nocturnes, Op.9.pdf");
    }

    @Test
    @DisplayName("경계: 뒤에 숫자가 이어지면 다른 번호다 — 제목 'zz연습곡 Op.10' 은 Op.1 을 담고 있지 않다")
    void digitBoundary_keepsBracket() throws Exception {
        Tokens admin = loginAdmin();
        long composerId = createComposer(admin, "경계테스트", "Boundaryomit, Zz");
        long editionId = recommendedEdition(admin, composerId, "zz연습곡 Op.10", "Zz Etudes, Op.10",
                List.of("Op.1"), Map.of());

        expectFileName(editionId, "경계테스트 - zz연습곡 Op.10 (Op.1).pdf");
    }

    @Test
    @DisplayName("경계: 제목이 작품번호보다 덜 자세하면 괄호를 남긴다 (§12-1 3)")
    void titleLessSpecific_keepsBracket() throws Exception {
        Tokens admin = loginAdmin();
        long composerId = createComposer(admin, "좁힘테스트", "Narrowomit, Zz");
        long editionId = recommendedEdition(admin, composerId, "zz녹턴 Op.9", "Zz Nocturnes",
                List.of("Op.9 No.2"), Map.of());

        expectFileName(editionId, "좁힘테스트 - zz녹턴 Op.9 (Op.9 No.2).pdf");
    }

    @Test
    @DisplayName("괄호를 생략해도 편곡·악장 접미사는 그대로 붙는다")
    void omission_keepsScopeSuffix() throws Exception {
        Tokens admin = loginAdmin();
        long composerId = createComposer(admin, "접미사테스트", "Suffixomit, Zz");
        long editionId = recommendedEdition(admin, composerId, "zz녹턴 Op.9", "Zz Nocturnes, Op.9",
                List.of("Op.9"), Map.of("kind", "ARRANGEMENT", "scope", "MOVEMENT", "movementNumber", 2));

        expectFileName(editionId, "접미사테스트 - zz녹턴 Op.9 편곡 2악장.pdf");
    }

    @Test
    @DisplayName("빈 괄호 () 는 어떤 조건에서도 파일명에 남지 않는다")
    void neverLeavesEmptyBrackets() throws Exception {
        Tokens admin = loginAdmin();
        long composerId = createComposer(admin, "빈괄호테스트", "Emptyomit, Zz");
        long noCatalog = recommendedEdition(admin, composerId, "zz짐노페디", "Zz Gymnopedies", List.of(), Map.of());
        long manyCatalog = recommendedEdition(admin, composerId, "zz사계", "Zz The Seasons",
                List.of("Op.37a", "Op.37b"), Map.of());

        expectFileName(noCatalog, "빈괄호테스트 - zz짐노페디.pdf");
        expectFileName(manyCatalog, "빈괄호테스트 - zz사계.pdf");
    }

    // ===== 헬퍼 (DownloadScopeSuffixIntegrationTest 와 같은 모양) =====

    private long recommendedEdition(Tokens admin, long composerId, String titleKo, String titleOriginal,
                                    List<String> catalogNumbers, Map<String, Object> overrides) throws Exception {
        long workId = createWork(admin, composerId, titleKo, titleOriginal,
                catalogNumbers, List.of(), "INTERMEDIATE", false);
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
