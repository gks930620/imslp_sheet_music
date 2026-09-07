package com.test.test.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.test.test.integration.support.AdminApiTestSupport;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 관리자 판본 API (02_API_명세서 §5-1 ~ §5-7 동기 부분, 01_ERD §3-3 추천 후보) — TDD Red.
 *
 * <p>업로드 상한은 {@code app.edition.max-file-bytes}(03 §5) 로 1MB 로 줄여 413 을 검증한다.
 * 상한 문구는 설정값에서 만든다: {@code "{MB}MB 이하만 올릴 수 있어요"} → 운영(100MB)에서는 명세 문구 그대로.
 *
 * <p>"파일 받아오기"의 비동기 흐름(202 → 폴링, 409, 라이선스 400)은 워커가 필요하므로 {@link EditionFetchFileApiIntegrationTest}.
 */
@TestPropertySource(properties = "app.edition.max-file-bytes=1048576")
class AdminEditionApiIntegrationTest extends AdminApiTestSupport {

    // ===== §5-1 업로드 =====

    @Test
    void upload_pdf_returns_201_with_page_count_and_preview() throws Exception {
        Tokens admin = loginAdmin();
        byte[] pdf = samplePdfBytes();

        JsonNode data = data(uploadEditionFile(admin, pdfPart("beethoven_op27-2.pdf", pdf))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true)));

        assertThat(data.path("fileId").asLong()).isPositive();
        assertThat(data.path("previewFileId").asLong()).isPositive();
        assertThat(data.path("fileName").asText()).isEqualTo("beethoven_op27-2.pdf");
        assertThat(data.path("fileSize").asLong()).isEqualTo(pdf.length);
        assertThat(data.path("pageCount").asInt()).isEqualTo(2);
        String previewUrl = data.path("previewUrl").asText();
        assertThat(previewUrl).startsWith("/uploads/").endsWith(".png");

        // 미리보기는 기존 서빙 프록시로 바로 열린다 (permitAll)
        mockMvc.perform(get(previewUrl))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG));
    }

    @Test
    void upload_rejects_non_pdf_and_empty_file() throws Exception {
        Tokens admin = loginAdmin();

        uploadEditionFile(admin, new MockMultipartFile("file", "notes.txt", MediaType.TEXT_PLAIN_VALUE,
                "hello".getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_RULE_VIOLATION"))
                .andExpect(jsonPath("$.message").value("PDF 파일만 올릴 수 있어요"));

        // 확장자만 pdf 이고 매직바이트가 아닌 것도 거부
        uploadEditionFile(admin, pdfPart("fake.pdf", "<html>not a pdf</html>".getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_RULE_VIOLATION"))
                .andExpect(jsonPath("$.message").value("PDF 파일만 올릴 수 있어요"));

        uploadEditionFile(admin, pdfPart("empty.pdf", new byte[0]))
                .andExpect(status().isBadRequest());
    }

    @Test
    void upload_over_limit_returns_413() throws Exception {
        Tokens admin = loginAdmin();
        byte[] sample = samplePdfBytes();
        byte[] big = Arrays.copyOf(sample, 1024 * 1024 + 1024);   // 유효한 헤더 + 1MB 초과 패딩

        uploadEditionFile(admin, pdfPart("huge.pdf", big))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("PAYLOAD_TOO_LARGE"))
                .andExpect(jsonPath("$.message").value("1MB 이하만 올릴 수 있어요"));
    }

    // ===== §5-2 판본 추가 =====

    @Test
    void create_edition_with_file_returns_201_admin_edition_dto() throws Exception {
        Tokens admin = loginAdmin();
        long workId = createWork(admin, createComposer(admin));
        JsonNode upload = uploadSamplePdf(admin);
        long fileId = upload.path("fileId").asLong();
        long previewFileId = upload.path("previewFileId").asLong();

        Map<String, Object> body = editionBody(fileId, previewFileId, 2, "UNKNOWN", null);
        body.put("publisher", "Breitkopf & Härtel");
        body.put("publishYear", 1862);
        body.put("plateNumber", "B.&H. 1234");
        body.put("editor", "Sigmund Lebert");
        body.put("scanner", "Unknown");
        body.put("imslpFileUrl", "https://imslp.org/wiki/Special:ImagefromIndex/00014");
        body.put("imslpCopyrightText", "Public Domain");
        body.put("ccLicenseName", "CC BY-SA 4.0");
        body.put("ccAttribution", "Viktor Keil");

        JsonNode data = data(adminPost(admin, "/api/admin/works/{workId}/editions", body, workId)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true)));

        long editionId = data.path("id").asLong();
        assertThat(editionId).isPositive();
        assertThat(data.path("kind").asText()).isEqualTo("COMPLETE_SCORE");
        assertThat(data.path("scope").asText()).isEqualTo("COMPLETE");
        assertThat(data.path("movementNumber").isNull()).isTrue();
        assertThat(data.path("sectionLabel").isNull()).isTrue();
        assertThat(data.path("pageCount").asInt()).isEqualTo(2);
        assertThat(data.path("fileSize").asLong()).isEqualTo(samplePdfBytes().length);
        assertThat(data.path("hasFile").asBoolean()).isTrue();
        assertThat(data.path("previewUrl").asText()).isEqualTo(upload.path("previewUrl").asText());
        assertThat(data.path("publisher").asText()).isEqualTo("Breitkopf & Härtel");
        assertThat(data.path("publishYear").asInt()).isEqualTo(1862);
        assertThat(data.path("plateNumber").asText()).isEqualTo("B.&H. 1234");
        assertThat(data.path("editor").asText()).isEqualTo("Sigmund Lebert");
        assertThat(data.path("arranger").isNull()).isTrue();
        assertThat(data.path("scanner").asText()).isEqualTo("Unknown");
        assertThat(data.path("koreaCopyright").asText()).isEqualTo("UNKNOWN");
        assertThat(data.path("imslpCopyrightText").asText()).isEqualTo("Public Domain");
        assertThat(data.path("ccLicenseName").asText()).isEqualTo("CC BY-SA 4.0");
        assertThat(data.path("ccAttribution").asText()).isEqualTo("Viktor Keil");
        assertThat(data.path("imslpFileUrl").asText()).isEqualTo("https://imslp.org/wiki/Special:ImagefromIndex/00014");
        assertThat(data.path("downloadable").asBoolean()).isFalse();   // UNKNOWN 이라 아직
        assertThat(data.path("largeFile").asBoolean()).isFalse();
        assertThat(data.path("downloadUrl").isNull()).isTrue();
        // AdminEditionDTO 추가 필드
        assertThat(data.path("pdfFileId").asLong()).isEqualTo(fileId);
        assertThat(data.path("previewFileId").asLong()).isEqualTo(previewFileId);
        assertThat(data.path("imslpFileId").isNull()).isTrue();
        assertThat(data.path("imslpLicenseCode").isNull()).isTrue();
        assertThat(data.path("copyrightNote").isNull()).isTrue();
        assertThat(data.path("fileFetchStatus").isNull()).isTrue();
        assertThat(data.path("fileFetchError").isNull()).isTrue();
        assertThat(data.path("downloadCount").asLong()).isZero();
        assertThat(data.path("isRecommended").asBoolean()).isFalse();
        assertThat(data.path("isCandidate").asBoolean()).isTrue();   // 전체 악보·전곡·파일 있음 → 추천 후보
        assertThat(data.path("createdAt").isTextual()).isTrue();
        assertThat(data.path("workStatus").asText()).isEqualTo("PREPARING");   // 추천 전

        // 곡 상세에서도 후보로 잡힌다
        JsonNode work = getWork(admin, workId);
        assertThat(work.path("candidateEditionId").asLong()).isEqualTo(editionId);
        assertThat(work.path("editions").get(0).path("isCandidate").asBoolean()).isTrue();
    }

    @Test
    void create_edition_validation_rules() throws Exception {
        Tokens admin = loginAdmin();
        long workId = createWork(admin, createComposer(admin));

        Map<String, Object> noKind = editionBody(null, null, null, "UNKNOWN", null);
        noKind.put("kind", null);
        adminPost(admin, "/api/admin/works/{workId}/editions", noKind, workId)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[?(@.field == 'kind')]").exists());

        Map<String, Object> noCopyright = editionBody(null, null, null, null, null);
        adminPost(admin, "/api/admin/works/{workId}/editions", noCopyright, workId)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field == 'koreaCopyright')]").exists());

        Map<String, Object> movementWithoutNumber = editionBody(null, null, null, "UNKNOWN", null);
        movementWithoutNumber.put("scope", "MOVEMENT");
        adminPost(admin, "/api/admin/works/{workId}/editions", movementWithoutNumber, workId)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[?(@.field == 'movementNumber')].message").value(org.hamcrest.Matchers.hasItem("악장 번호를 입력해 주세요")));

        movementWithoutNumber.put("movementNumber", 0);
        adminPost(admin, "/api/admin/works/{workId}/editions", movementWithoutNumber, workId)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field == 'movementNumber')]").exists());

        adminPost(admin, "/api/admin/works/{workId}/editions", editionBody(null, null, null, "FREE", "  "), workId)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[?(@.field == 'copyrightNote')].message").value(org.hamcrest.Matchers.hasItem("판정 근거를 적어 주세요")));

        adminPost(admin, "/api/admin/works/{workId}/editions", editionBody(null, null, null, "RESTRICTED", null), workId)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field == 'copyrightNote')]").exists());

        Map<String, Object> badUrl = editionBody(null, null, null, "UNKNOWN", null);
        badUrl.put("imslpFileUrl", "http://example.com/file.pdf");
        adminPost(admin, "/api/admin/works/{workId}/editions", badUrl, workId)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field == 'imslpFileUrl')]").exists());

        adminPost(admin, "/api/admin/works/{workId}/editions", editionBody(null, null, null, "UNKNOWN", null), 99_999_999L)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("NOT_FOUND"));
    }

    @Test
    void create_edition_rejects_unknown_or_already_linked_file() throws Exception {
        Tokens admin = loginAdmin();
        long workId = createWork(admin, createComposer(admin));

        adminPost(admin, "/api/admin/works/{workId}/editions", editionBody(99_999_999L, null, null, "UNKNOWN", null), workId)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_RULE_VIOLATION"));

        JsonNode upload = uploadSamplePdf(admin);
        long fileId = upload.path("fileId").asLong();
        createEdition(admin, workId, editionBody(fileId, upload.path("previewFileId").asLong(), 2, "UNKNOWN", null));

        // 같은 파일을 두 번째 판본에 연결 → 400
        adminPost(admin, "/api/admin/works/{workId}/editions", editionBody(fileId, null, 2, "UNKNOWN", null), workId)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_RULE_VIOLATION"));
    }

    @Test
    void movement_edition_stores_movement_number_and_file_less_edition_has_no_file() throws Exception {
        Tokens admin = loginAdmin();
        long workId = createWork(admin, createComposer(admin));
        Map<String, Object> body = editionBody(null, null, null, "UNKNOWN", null);
        body.put("scope", "MOVEMENT");
        body.put("movementNumber", 3);
        body.put("kind", "ARRANGEMENT");
        body.put("arranger", "Franz Liszt");

        JsonNode data = data(adminPost(admin, "/api/admin/works/{workId}/editions", body, workId).andExpect(status().isCreated()));
        assertThat(data.path("kind").asText()).isEqualTo("ARRANGEMENT");
        assertThat(data.path("scope").asText()).isEqualTo("MOVEMENT");
        assertThat(data.path("movementNumber").asInt()).isEqualTo(3);
        assertThat(data.path("arranger").asText()).isEqualTo("Franz Liszt");
        assertThat(data.path("hasFile").asBoolean()).isFalse();
        assertThat(data.path("fileSize").isNull()).isTrue();
        assertThat(data.path("pageCount").isNull()).isTrue();
        assertThat(data.path("previewUrl").isNull()).isTrue();
        assertThat(data.path("pdfFileId").isNull()).isTrue();
        assertThat(data.path("isCandidate").asBoolean()).isFalse();
    }

    // ===== §5-3 수정 / §5-4 조회 / §5-5 삭제 =====

    @Test
    void update_edition_replaces_file_and_records_copyright_judgement() throws Exception {
        Tokens admin = loginAdmin();
        long workId = createWork(admin, createComposer(admin));
        JsonNode firstUpload = uploadSamplePdf(admin);
        long firstFileId = firstUpload.path("fileId").asLong();
        long editionId = createEdition(admin, workId, editionBody(firstFileId, firstUpload.path("previewFileId").asLong(), 2, "UNKNOWN", null));

        JsonNode before = getEdition(admin, editionId);
        assertThat(before.path("copyrightJudgedAt").isNull()).isTrue();
        assertThat(before.path("workId").asLong()).isEqualTo(workId);
        assertThat(before.path("workStatus").asText()).isEqualTo("PREPARING");

        JsonNode secondUpload = uploadSamplePdf(admin);
        long secondFileId = secondUpload.path("fileId").asLong();
        Map<String, Object> update = editionBody(secondFileId, secondUpload.path("previewFileId").asLong(), 7, "FREE", "작곡가 1827 사망");
        update.put("editor", "Heinrich Schenker");

        JsonNode data = data(adminPut(admin, "/api/admin/editions/{id}", update, editionId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true)));
        assertThat(data.path("id").asLong()).isEqualTo(editionId);
        assertThat(data.path("pdfFileId").asLong()).isEqualTo(secondFileId);
        assertThat(data.path("pageCount").asInt()).isEqualTo(7);   // 요청값 우선
        assertThat(data.path("editor").asText()).isEqualTo("Heinrich Schenker");
        assertThat(data.path("koreaCopyright").asText()).isEqualTo("FREE");
        assertThat(data.path("copyrightNote").asText()).isEqualTo("작곡가 1827 사망");
        assertThat(data.path("copyrightJudgedAt").isTextual()).isTrue();
        assertThat(data.path("copyrightJudgedBy").asText()).isEqualTo(ADMIN_USERNAME);
        assertThat(data.path("downloadable").asBoolean()).isTrue();
        assertThat(data.path("downloadUrl").asText()).isEqualTo("/api/editions/" + editionId + "/download");

        // 옛 파일 행은 지워지고 새 파일은 살아 있다.
        // (공용 파일 API 는 EDITION 을 다루지 않으므로 — 02 §3-4, 2026-09-07 — files 행은 DB 로, 바이트는 정식 다운로드로 본다)
        assertThat(fileRowExists(firstFileId)).isFalse();
        assertThat(fileRowExists(secondFileId)).isTrue();
        mockMvc.perform(get("/api/editions/{id}/download", editionId))
                .andExpect(status().isOk())
                .andExpect(header().longValue(HttpHeaders.CONTENT_LENGTH, samplePdfBytes().length));

        adminPut(admin, "/api/admin/editions/{id}", update, 99_999_999L)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("NOT_FOUND"));
        adminGet(admin, "/api/admin/editions/{id}", 99_999_999L)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("NOT_FOUND"));
    }

    @Test
    void update_without_file_change_keeps_file_and_page_count_falls_back_to_new_file_value() throws Exception {
        Tokens admin = loginAdmin();
        long workId = createWork(admin, createComposer(admin));
        JsonNode upload = uploadSamplePdf(admin);
        long fileId = upload.path("fileId").asLong();
        long editionId = createEdition(admin, workId, editionBody(fileId, upload.path("previewFileId").asLong(), 2, "UNKNOWN", null));

        // 같은 fileId 로 다시 저장 → 교체 아님
        Map<String, Object> same = editionBody(fileId, upload.path("previewFileId").asLong(), null, "UNKNOWN", null);
        JsonNode data = data(adminPut(admin, "/api/admin/editions/{id}", same, editionId).andExpect(status().isOk()));
        assertThat(data.path("pdfFileId").asLong()).isEqualTo(fileId);
        assertThat(data.path("hasFile").asBoolean()).isTrue();
        // 파일이 그대로 살아 있다 — 미리보기 프록시로 확인(공용 파일 API 는 EDITION 을 다루지 않는다, §3-4)
        mockMvc.perform(get(upload.path("previewUrl").asText())).andExpect(status().isOk());

        // 새 파일로 교체하면서 pageCount 를 안 주면 새 파일 값(2)
        JsonNode second = uploadSamplePdf(admin);
        Map<String, Object> replace = editionBody(second.path("fileId").asLong(), second.path("previewFileId").asLong(), null, "UNKNOWN", null);
        JsonNode replaced = data(adminPut(admin, "/api/admin/editions/{id}", replace, editionId).andExpect(status().isOk()));
        assertThat(replaced.path("pageCount").asInt()).isEqualTo(2);
    }

    @Test
    void delete_edition_clears_recommendation_and_files() throws Exception {
        Tokens admin = loginAdmin();
        long workId = createWork(admin, createComposer(admin));
        JsonNode upload = uploadSamplePdf(admin);
        long fileId = upload.path("fileId").asLong();
        long editionId = createEdition(admin, workId, editionBody(fileId, upload.path("previewFileId").asLong(), 2, "FREE", "근거"));
        setRecommended(admin, workId, editionId);
        assertThat(getWork(admin, workId).path("status").asText()).isEqualTo("READY");

        adminDelete(admin, "/api/admin/editions/{id}", editionId).andExpect(status().isNoContent());

        adminGet(admin, "/api/admin/editions/{id}", editionId).andExpect(status().isNotFound());
        assertThat(fileRowExists(fileId)).isFalse();
        assertThat(fileRowExists(upload.path("previewFileId").asLong())).isFalse();
        JsonNode work = getWork(admin, workId);
        assertThat(work.path("recommendedEditionId").isNull()).isTrue();
        assertThat(work.path("status").asText()).isEqualTo("PREPARING");
        assertThat(work.path("editions")).isEmpty();

        adminDelete(admin, "/api/admin/editions/{id}", editionId).andExpect(status().isNotFound());
    }

    // ===== §5-6 추천 지정 =====

    @Test
    void recommended_edition_rules() throws Exception {
        Tokens admin = loginAdmin();
        long composerId = createComposer(admin);
        long workId = createWork(admin, composerId);
        long otherWorkId = createWork(admin, composerId);

        long infoOnly = createInfoEdition(admin, workId);
        adminPut(admin, "/api/admin/works/{workId}/recommended-edition", json("editionId", infoOnly), workId)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_RULE_VIOLATION"))
                .andExpect(jsonPath("$.message").value("파일이 없어 추천으로 지정할 수 없어요"));

        long otherWorksEdition = createFileEdition(admin, otherWorkId, "FREE", "근거");
        adminPut(admin, "/api/admin/works/{workId}/recommended-edition", json("editionId", otherWorksEdition), workId)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("NOT_FOUND"));
        adminPut(admin, "/api/admin/works/{workId}/recommended-edition", json("editionId", 99_999_999L), workId)
                .andExpect(status().isNotFound());

        long unknownEdition = createFileEdition(admin, workId, "UNKNOWN", null);
        adminPut(admin, "/api/admin/works/{workId}/recommended-edition", json("editionId", unknownEdition), workId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.workId").value(workId))
                .andExpect(jsonPath("$.data.previousEditionId").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data.editionId").value(unknownEdition))
                .andExpect(jsonPath("$.data.workStatus").value("UNKNOWN"))
                .andExpect(jsonPath("$.data.warning").value("NOT_DOWNLOADABLE"));

        long freeEdition = createFileEdition(admin, workId, "FREE", "근거");
        adminPut(admin, "/api/admin/works/{workId}/recommended-edition", json("editionId", freeEdition), workId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.previousEditionId").value(unknownEdition))
                .andExpect(jsonPath("$.data.editionId").value(freeEdition))
                .andExpect(jsonPath("$.data.workStatus").value("READY"))
                .andExpect(jsonPath("$.data.warning").value(org.hamcrest.Matchers.nullValue()));

        JsonNode work = getWork(admin, workId);
        assertThat(work.path("recommendedEditionId").asLong()).isEqualTo(freeEdition);
        assertThat(work.path("status").asText()).isEqualTo("READY");
        // 정렬: 추천 → 파일 있음 → 파일 없음
        assertThat(longs(work.path("editions"), "id")).containsExactly(freeEdition, unknownEdition, infoOnly);
        assertThat(work.path("editions").get(0).path("isRecommended").asBoolean()).isTrue();
        assertThat(work.path("editions").get(1).path("isRecommended").asBoolean()).isFalse();
    }

    // ===== §5-7 파일 받아오기 — 동기 검증 부분 =====

    @Test
    void fetch_file_rejects_edition_with_file_or_without_imslp_file_id() throws Exception {
        Tokens admin = loginAdmin();
        long workId = createWork(admin, createComposer(admin));

        long withFile = createFileEdition(admin, workId, "UNKNOWN", null);
        adminPost(admin, "/api/admin/editions/{id}/fetch-file", json(), withFile)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_RULE_VIOLATION"))
                .andExpect(jsonPath("$.message").value("이미 파일이 있는 판본이에요"));

        long infoOnly = createInfoEdition(admin, workId);   // 관리자 등록분은 imslpFileId 없음
        adminPost(admin, "/api/admin/editions/{id}/fetch-file", json(), infoOnly)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_RULE_VIOLATION"))
                .andExpect(jsonPath("$.message").value("IMSLP 파일 정보가 없어 받아올 수 없어요"));

        adminPost(admin, "/api/admin/editions/{id}/fetch-file", json(), 99_999_999L)
                .andExpect(status().isNotFound());
    }

    // ===== 계약 구멍 메우기 (qa 결함 D8·D10) =====

    /**
     * qa 결함 D8 — {@code file} 파트 없이 업로드하면 지금은 500 이다.
     * 필수 파트 누락은 클라이언트 잘못이므로 400 {@code MISSING_PARAMETER}(02 §0-2) 여야 한다.
     * ({@code MissingServletRequestPartException} 핸들러가 없어 최후의 보루로 떨어진다 —
     * 기존 {@code MissingServletRequestParameterException} 핸들러 옆에 하나 추가하면 된다.)
     */
    @Test
    void upload_without_file_part_returns_400_missing_parameter() throws Exception {
        Tokens admin = loginAdmin();

        mockMvc.perform(multipart("/api/admin/edition-files")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin.accessToken())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("MISSING_PARAMETER"))
                .andExpect(jsonPath("$.message").isString());
    }

    /**
     * qa 결함 D10 — {@code AdminEditionDTO} 가 {@code recommended}/{@code isRecommended} 를 <b>둘 다</b> 내보낸다
     * (필드의 {@code @JsonProperty} + Lombok 게터가 각각 프로퍼티로 잡혀서).
     * 계약(02 §4-7)에 있는 이름은 {@code isRecommended}/{@code isCandidate} 뿐이다 —
     * 계약 밖 필드는 세 스택이 서로 다른 이름에 의존하게 만든다.
     */
    @Test
    void adminEditionDto_serializes_only_contract_flag_names() throws Exception {
        Tokens admin = loginAdmin();
        long workId = createWork(admin, createComposer(admin));
        long editionId = makeReady(admin, workId);

        // §5-4 단건
        adminGet(admin, "/api/admin/editions/{id}", editionId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isRecommended").value(true))
                .andExpect(jsonPath("$.data.isCandidate").value(false))
                .andExpect(jsonPath("$.data.recommended").doesNotExist())
                .andExpect(jsonPath("$.data.candidate").doesNotExist());

        // §4-7 곡 상세의 editions[]
        adminGet(admin, "/api/admin/works/{id}", workId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.editions[0].isRecommended").value(true))
                .andExpect(jsonPath("$.data.editions[0].recommended").doesNotExist())
                .andExpect(jsonPath("$.data.editions[0].candidate").doesNotExist());
    }
    /**
     * qa 2차 결함 — 추천 판본에서 {@code fileId: null} 로 파일을 떼면 <b>"파일 없는 추천 판본"</b> 이 남는다.
     * 이 상태는 §5-6 이 애초에 금지한 것이라(같은 판본을 다시 추천으로 지정하면 400 으로 막힌다),
     * <b>API 로는 만들 수 없다고 선언한 상태를 다른 API 가 저장해 두는 모순</b>이다.
     * 관리 화면은 {@code missing} 에 {@code RECOMMENDED_EDITION} 이 없으니 "보완 필요" 로도 잡지 못해
     * 관리자가 곡이 준비된 줄 안다.
     *
     * <p>계약(02 §5-3, 2026-09-07 추가): §5-5(판본 삭제)와 같은 정리 — <b>추천도 함께 푼다</b>.
     */
    @Test
    void update_edition_removing_file_clears_recommendation() throws Exception {
        Tokens admin = loginAdmin();
        long workId = createWork(admin, createComposer(admin));
        JsonNode upload = uploadSamplePdf(admin);
        long fileId = upload.path("fileId").asLong();
        long editionId = createEdition(admin, workId,
                editionBody(fileId, upload.path("previewFileId").asLong(), 2, "FREE", "근거"));
        setRecommended(admin, workId, editionId);
        assertThat(getWork(admin, workId).path("status").asText()).isEqualTo("READY");

        // 판정·나머지는 그대로 두고 파일만 뗀다
        Map<String, Object> withoutFile = editionBody(null, null, null, "FREE", "근거");
        JsonNode saved = data(adminPut(admin, "/api/admin/editions/{id}", withoutFile, editionId)
                .andExpect(status().isOk()));
        assertThat(saved.path("hasFile").asBoolean()).isFalse();
        assertThat(saved.path("isRecommended").asBoolean()).isFalse();
        assertThat(saved.path("workStatus").asText()).isEqualTo("PREPARING");

        JsonNode work = getWork(admin, workId);
        assertThat(work.path("recommendedEditionId").isNull()).isTrue();
        assertThat(work.path("status").asText()).isEqualTo("PREPARING");
        assertThat(work.path("needsWork").asBoolean()).isTrue();
        assertThat(strings(work.path("missing"))).contains("RECOMMENDED_EDITION");
        assertThat(work.path("editions").get(0).path("isRecommended").asBoolean()).isFalse();

        // 같은 판본을 다시 추천으로 지정하려 하면 §5-6 이 막는다 = 방금 상태는 저장돼 있으면 안 되는 상태였다
        adminPut(admin, "/api/admin/works/{workId}/recommended-edition", json("editionId", editionId), workId)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("파일이 없어 추천으로 지정할 수 없어요"));
    }

    /** 다른 파일로 <b>교체</b>하는 경우에는 추천이 유지된다 — 파일 없는 상태가 되지 않는다(02 §5-3). */
    @Test
    void update_edition_replacing_file_keeps_recommendation() throws Exception {
        Tokens admin = loginAdmin();
        long workId = createWork(admin, createComposer(admin));
        long editionId = makeReady(admin, workId);

        JsonNode second = uploadSamplePdf(admin);
        Map<String, Object> replace = editionBody(second.path("fileId").asLong(),
                second.path("previewFileId").asLong(), null, "FREE", "근거");
        JsonNode saved = data(adminPut(admin, "/api/admin/editions/{id}", replace, editionId)
                .andExpect(status().isOk()));
        assertThat(saved.path("isRecommended").asBoolean()).isTrue();
        assertThat(saved.path("workStatus").asText()).isEqualTo("READY");
        assertThat(getWork(admin, workId).path("recommendedEditionId").asLong()).isEqualTo(editionId);
    }
}
