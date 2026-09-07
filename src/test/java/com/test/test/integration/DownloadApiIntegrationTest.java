package com.test.test.integration;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GET /api/editions/{id}/download — docs/설계/02_API_명세서.md §3-4.
 * 공개(비로그인) 1클릭 다운로드. 파일명 규칙, 카운터 증가, 403/404/503 과 503 시 카운터 불변.
 */
class DownloadApiIntegrationTest extends SheetMusicFixtureSupport {

    @Test
    @DisplayName("정상: 200, application/pdf, Content-Length, 바이트 동일, Content-Disposition 파일명 규칙")
    void download_ok_headersAndBytes() throws Exception {
        Tokens admin = loginAdmin();
        long composerId = createComposer(admin, "테스트작곡가", "Testcomposer, Zz");
        ReadyWork rw = createWorkWithRecommendedEdition(admin, composerId, "테스트 소나타", "Test Sonata in A",
                List.of("K.331/300i", "Op.99"), List.of(), "INTERMEDIATE", 1, "FREE");
        byte[] expected = samplePdfBytes();

        // 대표 작품번호 = sort_order 0 = 목록의 첫 번째, 슬래시는 '-' 로
        String expectedName = "테스트작곡가 - 테스트 소나타 (K.331-300i).pdf";
        MvcResult result = mockMvc.perform(get("/api/editions/{id}/download", rw.editionId()))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, containsString(MediaType.APPLICATION_PDF_VALUE)))
                .andExpect(header().longValue(HttpHeaders.CONTENT_LENGTH, expected.length))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("attachment")))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        containsString("filename=\"score-" + rw.editionId() + ".pdf\"")))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        containsString("filename*=UTF-8''" + rfc5987(expectedName))))
                .andExpect(content().bytes(expected))
                .andReturn();

        assertThat(result.getResponse().getContentAsByteArray()).startsWith("%PDF".getBytes(StandardCharsets.US_ASCII));
    }

    @Test
    @DisplayName("파일명: 작품번호 없으면 괄호 생략, 한국어 제목 없으면 원어 제목")
    void download_fileNameFallbacks() throws Exception {
        Tokens admin = loginAdmin();
        long composerId = createComposer(admin, "사티테스트", "Satietest, Zz");
        ReadyWork noCatalog = createWorkWithRecommendedEdition(admin, composerId, "짐노페디 테스트", "Zz Gymnopedies",
                List.of(), List.of(), "ELEMENTARY", 1, "FREE");
        ReadyWork noTitleKo = createWorkWithRecommendedEdition(admin, composerId, "", "Zz Gnossiennes",
                List.of("Op.7"), List.of(), "ELEMENTARY", 1, "FREE");

        mockMvc.perform(get("/api/editions/{id}/download", noCatalog.editionId()))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        containsString("filename*=UTF-8''" + rfc5987("사티테스트 - 짐노페디 테스트.pdf"))));

        mockMvc.perform(get("/api/editions/{id}/download", noTitleKo.editionId()))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        containsString("filename*=UTF-8''" + rfc5987("사티테스트 - Zz Gnossiennes (Op.7).pdf"))));
    }

    @Test
    @DisplayName("성공하면 판본·곡 download_count +1, download_log 기록(hasDownloadHistory) — 두 번 받으면 2")
    void download_incrementsCounters() throws Exception {
        Tokens admin = loginAdmin();
        long composerId = createComposer(admin, "카운터", "Counter, Zz");
        ReadyWork rw = createReadyWork(admin, composerId, "zz카운터 곡", "Zz Counter Piece", 1);

        JsonNode before = getJsonAsAdmin(admin, "/api/admin/works/{id}", rw.workId()).path("data");
        assertThat(before.path("downloadCount").asLong()).isZero();
        assertThat(before.path("hasDownloadHistory").asBoolean()).isFalse();

        mockMvc.perform(get("/api/editions/{id}/download", rw.editionId())).andExpect(status().isOk());

        JsonNode afterOne = getJsonAsAdmin(admin, "/api/admin/works/{id}", rw.workId()).path("data");
        assertThat(afterOne.path("downloadCount").asLong()).isEqualTo(1);
        assertThat(afterOne.path("hasDownloadHistory").asBoolean()).isTrue();
        assertThat(getJsonAsAdmin(admin, "/api/admin/editions/{id}", rw.editionId())
                .path("data").path("downloadCount").asLong()).isEqualTo(1);

        mockMvc.perform(get("/api/editions/{id}/download", rw.editionId())).andExpect(status().isOk());

        assertThat(getJsonAsAdmin(admin, "/api/admin/works/{id}", rw.workId())
                .path("data").path("downloadCount").asLong()).isEqualTo(2);
        assertThat(getJsonAsAdmin(admin, "/api/admin/editions/{id}", rw.editionId())
                .path("data").path("downloadCount").asLong()).isEqualTo(2);
    }

    /**
     * HEAD /api/editions/{id}/download — 화면이 "받을 수 있는지" 를 먼저 묻는 경로 (02 §3-4, 03 §13).
     *
     * <p>왜 필요한가: 다운로드는 {@code <a href download>} 라 실패(503 등)를 감지할 수 없다.
     * 화면은 클릭과 함께 HEAD 로 한 번 물어보고, 실패면 03_곡상세 §동작·통신 상태의 안내를 띄운다(qa 결함 D5).
     *
     * <p>계약: GET 과 <b>같은 상태코드·헤더</b>, 본문 없음, 그리고 <b>다운로드 수를 올리지 않는다</b>.
     * (Spring 은 HEAD 를 GET 핸들러로 보내므로 그냥 두면 HEAD 로도 카운터가 올라 한 번 받을 때 2 가 된다.)
     * MockMvc 는 실제 컨테이너와 달리 HEAD 응답 본문을 버리지 않으므로 여기서 본문 없음은 검증하지 않는다.
     */
    @Test
    @DisplayName("HEAD 정상: 200 + GET 과 같은 헤더, 다운로드 수는 그대로")
    void head_ok_sameHeaders_andDoesNotCount() throws Exception {
        Tokens admin = loginAdmin();
        long composerId = createComposer(admin, "헤드확인", "Headcheck, Zz");
        ReadyWork rw = createReadyWork(admin, composerId, "zz헤드 곡", "Zz Head Piece", 1);

        mockMvc.perform(head("/api/editions/{id}/download", rw.editionId()))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, containsString(MediaType.APPLICATION_PDF_VALUE)))
                .andExpect(header().longValue(HttpHeaders.CONTENT_LENGTH, samplePdfBytes().length))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("attachment")));

        JsonNode work = getJsonAsAdmin(admin, "/api/admin/works/{id}", rw.workId()).path("data");
        assertThat(work.path("downloadCount").asLong()).isZero();
        assertThat(work.path("hasDownloadHistory").asBoolean()).isFalse();
        assertThat(getJsonAsAdmin(admin, "/api/admin/editions/{id}", rw.editionId())
                .path("data").path("downloadCount").asLong()).isZero();

        // 이어서 실제로 받으면 그때 1 (사전 확인이 카운터를 부풀리지 않는다)
        mockMvc.perform(get("/api/editions/{id}/download", rw.editionId())).andExpect(status().isOk());
        assertThat(getJsonAsAdmin(admin, "/api/admin/works/{id}", rw.workId())
                .path("data").path("downloadCount").asLong()).isEqualTo(1);
    }

    @Test
    @DisplayName("HEAD 실패 경로: 없는 판본 404 / 저작권 제한 403 / 바이트 없음 503 — GET 과 같은 상태코드")
    void head_errorsMatchGet() throws Exception {
        Tokens admin = loginAdmin();
        long composerId = createComposer(admin, "헤드실패", "Headfail, Zz");
        ReadyWork restricted = createWorkWithRecommendedEdition(admin, composerId, "zz헤드 제한곡", "Zz Head Restricted",
                List.of(), List.of(), "INTERMEDIATE", 1, "RESTRICTED");
        ReadyWork lost = createReadyWork(admin, composerId, "zz헤드 유실곡", "Zz Head Lost Bytes", 1);

        mockMvc.perform(head("/api/editions/{id}/download", 999_999_999L))
                .andExpect(status().isNotFound());

        mockMvc.perform(head("/api/editions/{id}/download", restricted.editionId()))
                .andExpect(status().isForbidden());

        // files 행은 그대로 두고 디스크 바이트만 지운다 → 503
        Path uploadRoot = Path.of("build", "test-uploads").toAbsolutePath().normalize();
        try (Stream<Path> files = Files.list(uploadRoot)) {
            for (Path p : files.toList()) {
                Files.deleteIfExists(p);
            }
        }

        mockMvc.perform(head("/api/editions/{id}/download", lost.editionId()))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    @DisplayName("추천이 아닌 다른 판본도 파일+FREE 면 받아진다")
    void download_otherEditionAlsoWorks() throws Exception {
        Tokens admin = loginAdmin();
        long composerId = createComposer(admin, "다른판본", "Otheredition, Zz");
        ReadyWork rw = createReadyWork(admin, composerId, "zz다른판본 곡", "Zz Other Edition Piece", 1);
        long other = createEdition(admin, rw.workId(), uploadSamplePdf(admin), 1, "FREE", null);

        mockMvc.perform(get("/api/editions/{id}/download", other))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("score-" + other + ".pdf")));
    }

    @Test
    @DisplayName("koreaCopyright != FREE (RESTRICTED / UNKNOWN) → 403 COPYRIGHT_RESTRICTED, 카운터 불변")
    void download_copyrightRestricted_403() throws Exception {
        Tokens admin = loginAdmin();
        long composerId = createComposer(admin, "저작권", "Copyright, Zz");
        ReadyWork restricted = createWorkWithRecommendedEdition(admin, composerId, "zz제한곡", "Zz Restricted Piece",
                List.of(), List.of(), "INTERMEDIATE", 1, "RESTRICTED");
        ReadyWork unknown = createWorkWithRecommendedEdition(admin, composerId, "zz확인중곡", "Zz Unknown Piece",
                List.of(), List.of(), "INTERMEDIATE", 1, "UNKNOWN");

        mockMvc.perform(get("/api/editions/{id}/download", restricted.editionId()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("COPYRIGHT_RESTRICTED"));

        mockMvc.perform(get("/api/editions/{id}/download", unknown.editionId()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("COPYRIGHT_RESTRICTED"));

        assertThat(getJsonAsAdmin(admin, "/api/admin/works/{id}", restricted.workId())
                .path("data").path("downloadCount").asLong()).isZero();
    }

    @Test
    @DisplayName("없는 판본 → 404 NOT_FOUND")
    void download_unknownEdition_404() throws Exception {
        mockMvc.perform(get("/api/editions/{id}/download", 999_999_999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("파일 없는 판본(pdfFileId=null) → 404 NOT_FOUND")
    void download_editionWithoutFile_404() throws Exception {
        Tokens admin = loginAdmin();
        long composerId = createComposer(admin, "파일없음", "Nofile, Zz");
        long workId = createWork(admin, composerId, "zz파일없는 곡", "Zz No File Piece");
        long edition = createEdition(admin, workId, null, null, "FREE", null);

        mockMvc.perform(get("/api/editions/{id}/download", edition))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("숨김 곡의 판본 → 404 NOT_FOUND")
    void download_hiddenWork_404() throws Exception {
        Tokens admin = loginAdmin();
        long composerId = createComposer(admin, "숨김다운", "Hiddendl, Zz");
        long workId = createWork(admin, composerId, "zz숨김 곡", "Zz Hidden Piece", List.of(), List.of(), null, true);
        long edition = createEdition(admin, workId, uploadSamplePdf(admin), 1, "FREE", null);

        mockMvc.perform(get("/api/editions/{id}/download", edition))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("files 행은 있는데 바이트가 없음 → 503 FILE_UNAVAILABLE, 다운로드 수는 올리지 않는다")
    void download_bytesMissing_503_noCounterChange() throws Exception {
        Tokens admin = loginAdmin();
        long composerId = createComposer(admin, "유실", "Lostbytes, Zz");
        ReadyWork rw = createReadyWork(admin, composerId, "zz유실 곡", "Zz Lost Bytes Piece", 1);

        // 저장 전략(로컬 폴백)이 쓴 바이트를 디스크에서 지운다 — DB 의 files 행은 그대로
        Path uploadRoot = Path.of("build", "test-uploads").toAbsolutePath().normalize();
        try (Stream<Path> files = Files.list(uploadRoot)) {
            for (Path p : files.toList()) {
                Files.deleteIfExists(p);
            }
        }

        mockMvc.perform(get("/api/editions/{id}/download", rw.editionId()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("FILE_UNAVAILABLE"));

        JsonNode work = getJsonAsAdmin(admin, "/api/admin/works/{id}", rw.workId()).path("data");
        assertThat(work.path("downloadCount").asLong()).isZero();
        assertThat(work.path("hasDownloadHistory").asBoolean()).isFalse();
        assertThat(getJsonAsAdmin(admin, "/api/admin/editions/{id}", rw.editionId())
                .path("data").path("downloadCount").asLong()).isZero();
    }

    /**
     * RFC 5987 ext-value 인코딩: attr-char(영숫자 ! # $ &amp; + - . ^ _ ` | ~)는 그대로, 나머지는 UTF-8 퍼센트 인코딩.
     * (Spring {@code ContentDisposition} 의 filename* 인코딩과 같은 규칙 — 공백은 %20, 괄호는 %28 %29)
     */
    private static String rfc5987(String s) {
        StringBuilder sb = new StringBuilder();
        for (byte b : s.getBytes(StandardCharsets.UTF_8)) {
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

    @Test
    @DisplayName("우회 차단: 판본 파일 바이트·경로는 공용 파일 API(/api/files/**)로 새어 나가지 않는다")
    void editionFilesAreNotReachableThroughGenericFileApi() throws Exception {
        Tokens admin = loginAdmin();
        long composerId = createComposer(admin, "우회테스트", "Bypasstest, Zz");
        ReadyWork rw = createWorkWithRecommendedEdition(admin, composerId, "제한된 판본 곡", "Zz Restricted Work",
                List.of(), List.of(), "INTERMEDIATE", 1, "RESTRICTED");

        // 정식 경로는 저작권 게이트가 막는다 (§3-4)
        mockMvc.perform(get("/api/editions/{id}/download", rw.editionId()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("COPYRIGHT_RESTRICTED"));

        // 공용 파일 API 는 커뮤니티·사용자 파일 전용이다(FileService.verifyOwnership 의 EDITION 규칙과 같은 기준).
        // 순번 fileId 로 판본 PDF 바이트를 받아 게이트를 우회할 수 있으면 안 된다.
        mockMvc.perform(get("/api/files/{fileId}/content", rw.pdf().fileId()))
                .andExpect(status().isNotFound());

        // 저장 파일명(/uploads/{key})도 알려주지 않는다 — 알면 서빙 프록시로 바로 받을 수 있다.
        mockMvc.perform(get("/api/files/paths")
                        .param("refId", String.valueOf(rw.editionId()))
                        .param("refType", "EDITION"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
        mockMvc.perform(get("/api/files")
                        .param("refId", String.valueOf(rw.editionId()))
                        .param("refType", "EDITION"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }
}
