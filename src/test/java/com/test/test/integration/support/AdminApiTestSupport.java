package com.test.test.integration.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.test.test.integration.ApiIntegrationTestSupport;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 관리자 API 통합테스트 공통 헬퍼 — 02_API_명세서 §4·§5 의 요청 형식을 한 곳에 둔다.
 * (요청 JSON 의 필드명이 여기 한 곳에만 있으므로, 계약이 바뀌면 이 파일과 명세서를 함께 고친다.)
 *
 * <p>기본 컨텍스트(시드 적재됨, 테스트 트랜잭션 롤백)에서 쓰는 헬퍼. 워커가 도는 테스트는 {@link CrawlTestSupport}.
 */
public abstract class AdminApiTestSupport extends ApiIntegrationTestSupport {

    @PersistenceContext
    private EntityManager entityManager;

    /** 시드 ADMIN 계정 (data-user-roles.sql 의 (3,'ADMIN')). 판정자·작업 생성자 검증용. */
    protected static final String ADMIN_USERNAME = "gks930620";

    // ===== JSON 유틸 =====

    protected String uniq() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    /** null 값을 허용하는 순서 보존 맵 ({@code Map.of} 는 null 금지). */
    protected Map<String, Object> json(Object... keyValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put((String) keyValues[i], keyValues[i + 1]);
        }
        return map;
    }

    protected String body(Map<String, Object> map) {
        try {
            return objectMapper.writeValueAsString(map);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    protected JsonNode root(MvcResult result) {
        try {
            return objectMapper.readTree(result.getResponse().getContentAsString());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    protected JsonNode data(MvcResult result) {
        return root(result).path("data");
    }

    protected JsonNode data(ResultActions actions) {
        return data(actions.andReturn());
    }

    protected List<String> strings(JsonNode array) {
        List<String> list = new ArrayList<>();
        array.forEach(n -> list.add(n.isNull() ? null : n.asText()));
        return list;
    }

    /**
     * files 행이 남아 있는지 — 판본 파일의 "옛 행 삭제"(02 §5-3·§5-5·§4-9)를 확인하는 유일한 관찰 수단.
     *
     * <p>02 §3-4(2026-09-07)가 공용 파일 API 에서 EDITION 을 제외하면서 {@code /api/files/{id}/content} 로는
     * 판본 파일의 존재를 볼 수 없게 됐다(항상 404). 저장 바이트 삭제는 {@code afterCommit} 에 걸려 있어
     * 롤백되는 테스트 트랜잭션에서는 일어나지 않으므로 {@code /uploads/…} 로도 볼 수 없다.
     * 그래서 이 한 가지 부수효과만 DB 로 직접 관찰한다(HTTP 로 드러나지 않는 계약).
     */
    protected boolean fileRowExists(long fileId) {
        // JPQL 로 세는 이유: 테스트와 요청이 같은 트랜잭션을 쓰므로 서비스가 만든 delete 가 아직 flush 되지 않았을 수 있다.
        // JPQL 은 files 를 건드리기 전에 자동 flush 한다(네이티브 SQL 은 안 한다).
        Long count = entityManager
                .createQuery("select count(f) from FileEntity f where f.id = :id", Long.class)
                .setParameter("id", fileId)
                .getSingleResult();
        return count != null && count > 0;
    }

    /**
     * files 행의 웹 경로({@code /uploads/{저장파일명}}) — 02 §3-4 의 "{@code /uploads} 는 미리보기 전용" 게이트를
     * 시험하는 유일한 수단.
     *
     * <p>판본 PDF 의 저장 파일명은 <b>어떤 API 로도 알 수 없는 것이 계약</b>이다(§3-4 우회 차단).
     * 그래서 "이름을 손에 넣은 사람" 을 재현하려면 DB 에서 직접 읽는 수밖에 없다
     * (qa 3차는 서버의 {@code uploads/} 폴더를 그대로 훑어 이름을 얻었다).
     */
    protected String storedWebPath(long fileId) {
        return entityManager
                .createQuery("select f.filePath from FileEntity f where f.id = :id", String.class)
                .setParameter("id", fileId)
                .getSingleResult();
    }

    protected List<Long> longs(JsonNode array, String field) {
        List<Long> list = new ArrayList<>();
        array.forEach(n -> list.add(n.path(field).asLong()));
        return list;
    }

    // ===== 인증 헤더 포함 요청 =====

    protected ResultActions adminGet(Tokens tokens, String url, Object... vars) throws Exception {
        return mockMvc.perform(get(url, vars).header(HttpHeaders.AUTHORIZATION, bearer(tokens.accessToken())));
    }

    /** 쿼리 파라미터가 있는 GET — {@code adminQuery(t, "/api/admin/works", "status", "READY", "page", "0")}. */
    protected ResultActions adminQuery(Tokens tokens, String url, String... keyValues) throws Exception {
        MockHttpServletRequestBuilder builder = get(url).header(HttpHeaders.AUTHORIZATION, bearer(tokens.accessToken()));
        for (int i = 0; i < keyValues.length; i += 2) {
            builder.param(keyValues[i], keyValues[i + 1]);
        }
        return mockMvc.perform(builder);
    }

    protected ResultActions adminPost(Tokens tokens, String url, Map<String, Object> json, Object... vars) throws Exception {
        return mockMvc.perform(post(url, vars)
                .header(HttpHeaders.AUTHORIZATION, bearer(tokens.accessToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(json)));
    }

    protected ResultActions adminPut(Tokens tokens, String url, Map<String, Object> json, Object... vars) throws Exception {
        return mockMvc.perform(put(url, vars)
                .header(HttpHeaders.AUTHORIZATION, bearer(tokens.accessToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(json)));
    }

    protected ResultActions adminDelete(Tokens tokens, String url, Object... vars) throws Exception {
        return mockMvc.perform(delete(url, vars).header(HttpHeaders.AUTHORIZATION, bearer(tokens.accessToken())));
    }

    // ===== 작곡가 (§4-4) =====

    protected Map<String, Object> composerBody(String nameKo, String nameOriginal) {
        return json(
                "nameKo", nameKo,
                "nameOriginal", nameOriginal,
                "aliases", List.of(),
                "birthYear", 1800,
                "deathYear", 1850,
                "nationality", "테스트국",
                "imslpUrl", null);
    }

    protected long createComposer(Tokens tokens, Map<String, Object> body) throws Exception {
        return data(adminPost(tokens, "/api/admin/composers", body).andExpect(status().isCreated())).path("id").asLong();
    }

    /** 시드와 겹치지 않는 고유한 원어 표기로 작곡가 1명. */
    protected long createComposer(Tokens tokens) throws Exception {
        String id = uniq();
        return createComposer(tokens, composerBody("테스트작곡가" + id, "Testcomposer, " + id));
    }

    // ===== 곡 (§4-8) =====

    protected Map<String, Object> workBody(long composerId, String titleKo, String titleOriginal) {
        return json(
                "composerId", composerId,
                "titleKo", titleKo,
                "titleOriginal", titleOriginal,
                "catalogNumbers", List.of("Op.1"),
                "aliases", List.of("별칭" + uniq()),
                "level", "INTERMEDIATE",
                "compositionYear", null,
                "musicalKey", null,
                "movements", null,
                "movementPageGuide", null,
                "imslpUrl", null,
                "hidden", false);
    }

    protected long createWork(Tokens tokens, Map<String, Object> body) throws Exception {
        return data(adminPost(tokens, "/api/admin/works", body).andExpect(status().isCreated())).path("id").asLong();
    }

    /** 한국어 제목·별칭·난이도가 모두 채워진(= 추천 판본만 없는) 곡 1개. */
    protected long createWork(Tokens tokens, long composerId) throws Exception {
        String id = uniq();
        return createWork(tokens, workBody(composerId, "테스트곡 " + id, "Test Piece " + id));
    }

    protected JsonNode getWork(Tokens tokens, long workId) throws Exception {
        return data(adminGet(tokens, "/api/admin/works/{id}", workId).andExpect(status().isOk()));
    }

    // ===== 파일·판본 (§5-1 ~ 5-3, 5-6) =====

    protected byte[] samplePdfBytes() {
        try {
            return new ClassPathResource(FakeImslpClient.SAMPLE_PDF_RESOURCE).getContentAsByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    protected MockMultipartFile pdfPart(String fileName, byte[] bytes) {
        return new MockMultipartFile("file", fileName, MediaType.APPLICATION_PDF_VALUE, bytes);
    }

    protected ResultActions uploadEditionFile(Tokens tokens, MockMultipartFile part) throws Exception {
        return mockMvc.perform(multipart("/api/admin/edition-files")
                .file(part)
                .header(HttpHeaders.AUTHORIZATION, bearer(tokens.accessToken())));
    }

    /** 샘플 PDF(2쪽) 업로드 → §5-1 응답 data. */
    protected JsonNode uploadSamplePdf(Tokens tokens) throws Exception {
        return data(uploadEditionFile(tokens, pdfPart("sample-crawl.pdf", samplePdfBytes())).andExpect(status().isCreated()));
    }

    protected Map<String, Object> editionBody(Long fileId, Long previewFileId, Integer pageCount,
                                              String koreaCopyright, String copyrightNote) {
        return json(
                "fileId", fileId,
                "previewFileId", previewFileId,
                "kind", "COMPLETE_SCORE",
                "scope", "COMPLETE",
                "movementNumber", null,
                "pageCount", pageCount,
                "publisher", null,
                "publishYear", null,
                "plateNumber", null,
                "editor", null,
                "arranger", null,
                "scanner", null,
                "imslpFileUrl", null,
                "imslpCopyrightText", null,
                "koreaCopyright", koreaCopyright,
                "copyrightNote", copyrightNote,
                "ccLicenseName", null,
                "ccAttribution", null);
    }

    protected long createEdition(Tokens tokens, long workId, Map<String, Object> body) throws Exception {
        return data(adminPost(tokens, "/api/admin/works/{workId}/editions", body, workId)
                .andExpect(status().isCreated())).path("id").asLong();
    }

    /** 샘플 PDF 를 올려 파일 있는 판본 1개. */
    protected long createFileEdition(Tokens tokens, long workId, String koreaCopyright, String copyrightNote) throws Exception {
        JsonNode upload = uploadSamplePdf(tokens);
        return createEdition(tokens, workId, editionBody(
                upload.path("fileId").asLong(), upload.path("previewFileId").asLong(), upload.path("pageCount").asInt(),
                koreaCopyright, copyrightNote));
    }

    /** 파일 없는(정보만) 판본 1개, 판정 UNKNOWN. */
    protected long createInfoEdition(Tokens tokens, long workId) throws Exception {
        return createEdition(tokens, workId, editionBody(null, null, null, "UNKNOWN", null));
    }

    /**
     * 현재 판본(§5-4 {@code AdminEditionDTO})을 그대로 다시 저장하는 §5-3 요청 본문.
     * PUT 은 전체 교체라, 한 필드만 바꾸는 시나리오는 나머지를 현재 값으로 채워야 한다.
     */
    protected Map<String, Object> editionSaveBodyFrom(JsonNode edition) {
        return json(
                "fileId", nullableLong(edition, "pdfFileId"),
                "previewFileId", nullableLong(edition, "previewFileId"),
                "kind", text(edition, "kind"),
                "scope", text(edition, "scope"),
                "movementNumber", nullableInt(edition, "movementNumber"),
                "pageCount", nullableInt(edition, "pageCount"),
                "publisher", text(edition, "publisher"),
                "publishYear", nullableInt(edition, "publishYear"),
                "plateNumber", text(edition, "plateNumber"),
                "editor", text(edition, "editor"),
                "arranger", text(edition, "arranger"),
                "scanner", text(edition, "scanner"),
                "imslpFileUrl", text(edition, "imslpFileUrl"),
                "imslpCopyrightText", text(edition, "imslpCopyrightText"),
                "koreaCopyright", text(edition, "koreaCopyright"),
                "copyrightNote", text(edition, "copyrightNote"),
                "ccLicenseName", text(edition, "ccLicenseName"),
                "ccAttribution", text(edition, "ccAttribution"));
    }

    protected static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isNull() || value.isMissingNode() ? null : value.asText();
    }

    protected static Long nullableLong(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isNull() || value.isMissingNode() ? null : value.asLong();
    }

    protected static Integer nullableInt(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isNull() || value.isMissingNode() ? null : value.asInt();
    }

    protected JsonNode getEdition(Tokens tokens, long editionId) throws Exception {
        return data(adminGet(tokens, "/api/admin/editions/{id}", editionId).andExpect(status().isOk()));
    }

    protected JsonNode setRecommended(Tokens tokens, long workId, long editionId) throws Exception {
        return data(adminPut(tokens, "/api/admin/works/{workId}/recommended-edition", json("editionId", editionId), workId)
                .andExpect(status().isOk()));
    }

    /** 바로 받기 가능한 곡(READY): 파일 있는 FREE 판본을 추천으로. 반환은 판본 id. */
    protected long makeReady(Tokens tokens, long workId) throws Exception {
        long editionId = createFileEdition(tokens, workId, "FREE", "테스트 판정 근거");
        setRecommended(tokens, workId, editionId);
        return editionId;
    }
}
