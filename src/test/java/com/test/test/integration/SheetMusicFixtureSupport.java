package com.test.test.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.test.test.sheetmusic.seed.SeedCsvReader;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 공개 API(검색·상세·인기·다운로드·작곡가) 테스트가 쓰는 데이터 픽스처.
 *
 * <p>시드(CSV)에는 판본·파일이 없으므로, 판본이 필요한 시나리오는 <b>관리자 API 계약(02 §4·§5)</b> 만으로
 * 만든다. SQL 픽스처를 쓰지 않는 이유: 스키마(컬럼명)에 결합되지 않고, 관리 API 자체가 계약대로 동작해야만
 * 공개 API 테스트가 Green 이 되므로 두 계약이 서로를 검증한다.
 *
 * <p>모든 요청은 테스트 트랜잭션 안에서 롤백된다. 업로드된 바이트는 {@code ./build/test-uploads} 에 쓰이며
 * {@link ApiIntegrationTestSupport#cleanupUploadedFiles()} 가 지운다.
 */
public abstract class SheetMusicFixtureSupport extends ApiIntegrationTestSupport {

    protected static final String SAMPLE_PDF_CLASSPATH = "/imslp/sample.pdf";
    private static final String COMPOSERS_CSV = "seed/composers.csv";
    private static final String WORKS_CSV = "seed/works.csv";

    /** 02 §5-1 업로드 응답. */
    protected record UploadedPdf(long fileId, Long previewFileId, long fileSize, Integer pageCount, String previewUrl) {
    }

    /** 추천 판본까지 갖춘 곡. */
    protected record ReadyWork(long workId, long editionId, UploadedPdf pdf) {
    }

    /**
     * 시드 CSV 를 직접 읽는 리더 ({@code SeedLoader} 가 실제로 쓰는 것과 같은 빈).
     * 시드가 커질 때마다 테스트의 하드코딩 숫자를 손으로 맞추는 대신, "로더가 읽는 CSV 행 수"를
     * 테스트도 같은 소스에서 세게 한다 — 시드 개수 자체가 아니라 "로더가 CSV 를 빠짐없이 실었는가"를 잠근다.
     */
    @Autowired
    protected SeedCsvReader seedCsvReader;

    /** 시드 작곡가 총 수 (composers.csv 행 수). */
    protected int seedComposerCount() {
        return seedCsvReader.read(COMPOSERS_CSV).size();
    }

    /** 시드 곡 총 수 (works.csv 행 수). */
    protected int seedWorkCount() {
        return seedCsvReader.read(WORKS_CSV).size();
    }

    /** composers.csv 원본 행 (헤더: name_ko, name_original, birth_year, death_year, aliases, nationality). */
    protected List<Map<String, String>> seedComposerRows() {
        return seedCsvReader.read(COMPOSERS_CSV);
    }

    /** works.csv 원본 행 (헤더: seq, composer_original, title_ko, title_original, …). */
    protected List<Map<String, String>> seedWorkRows() {
        return seedCsvReader.read(WORKS_CSV);
    }

    /** composer_original 이 일치하는 시드 곡 수 (예: "Chopin, Frédéric"). */
    protected int seedWorkCountFor(String composerOriginal) {
        int count = 0;
        for (Map<String, String> row : seedCsvReader.read(WORKS_CSV)) {
            if (composerOriginal.equals(row.get("composer_original"))) {
                count++;
            }
        }
        return count;
    }

    // ===== 조회 헬퍼 =====

    protected JsonNode getJson(String urlTemplate, Object... uriVars) throws Exception {
        MvcResult result = mockMvc.perform(get(urlTemplate, uriVars).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    protected JsonNode getJsonAsAdmin(Tokens admin, String urlTemplate, Object... uriVars) throws Exception {
        MvcResult result = mockMvc.perform(get(urlTemplate, uriVars)
                        .accept(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin.accessToken())))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    /** GET /api/works/search?q= 의 works.content 를 반환. */
    protected JsonNode searchWorks(String q) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/works/search").param("q", q))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("works").path("content");
    }

    protected List<Long> searchWorkIds(String q) throws Exception {
        List<Long> ids = new ArrayList<>();
        for (JsonNode item : searchWorks(q)) {
            ids.add(item.path("id").asLong());
        }
        return ids;
    }

    /** 시드 곡 id 를 검색으로 찾는다 (titleOriginal 정확 일치). */
    protected long findSeedWorkId(String q, String titleOriginal) throws Exception {
        for (JsonNode item : searchWorks(q)) {
            if (titleOriginal.equals(item.path("titleOriginal").asText())) {
                return item.path("id").asLong();
            }
        }
        throw new AssertionError("시드 곡을 검색으로 찾지 못함: q=" + q + ", titleOriginal=" + titleOriginal);
    }

    /** 시드 작곡가 id 를 GET /api/composers 에서 찾는다 (nameOriginal 정확 일치). */
    protected long findSeedComposerId(String nameOriginal) throws Exception {
        for (JsonNode item : getJson("/api/composers").path("data").path("composers")) {
            if (nameOriginal.equals(item.path("nameOriginal").asText())) {
                return item.path("id").asLong();
            }
        }
        throw new AssertionError("시드 작곡가를 찾지 못함: " + nameOriginal);
    }

    // ===== 관리자 API 로 만드는 픽스처 (02 §4·§5 계약만 사용) =====

    protected long createComposer(Tokens admin, String nameKo, String nameOriginal) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("nameKo", nameKo);
        body.put("nameOriginal", nameOriginal);
        body.put("aliases", List.of());
        body.put("birthYear", 1800);
        body.put("deathYear", 1850);
        body.put("nationality", "테스트");
        body.put("imslpUrl", "https://imslp.org/wiki/Category:" + nameOriginal.replace(' ', '_'));
        MvcResult result = mockMvc.perform(post("/api/admin/composers")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("id").asLong();
    }

    protected long createWork(Tokens admin, long composerId, String titleKo, String titleOriginal,
                              List<String> catalogNumbers, List<String> aliases, String level, boolean hidden)
            throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("composerId", composerId);
        body.put("titleKo", titleKo);
        body.put("titleOriginal", titleOriginal);
        body.put("catalogNumbers", catalogNumbers);
        body.put("aliases", aliases);
        body.put("level", level);
        body.put("compositionYear", null);
        body.put("musicalKey", null);
        body.put("movements", null);
        body.put("movementPageGuide", null);
        body.put("imslpUrl", null);
        body.put("hidden", hidden);
        MvcResult result = mockMvc.perform(post("/api/admin/works")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("id").asLong();
    }

    protected long createWork(Tokens admin, long composerId, String titleKo, String titleOriginal) throws Exception {
        return createWork(admin, composerId, titleKo, titleOriginal, List.of(), List.of(), "INTERMEDIATE", false);
    }

    protected byte[] samplePdfBytes() throws Exception {
        try (InputStream in = getClass().getResourceAsStream(SAMPLE_PDF_CLASSPATH)) {
            if (in == null) {
                throw new AssertionError("테스트 픽스처 없음: " + SAMPLE_PDF_CLASSPATH);
            }
            return in.readAllBytes();
        }
    }

    /** POST /api/admin/edition-files (multipart 필드명 file) — 02 §5-1. */
    protected UploadedPdf uploadSamplePdf(Tokens admin) throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "sample.pdf", MediaType.APPLICATION_PDF_VALUE, samplePdfBytes());
        MvcResult result = mockMvc.perform(multipart("/api/admin/edition-files")
                        .file(file)
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin.accessToken())))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
        return new UploadedPdf(
                data.path("fileId").asLong(),
                data.path("previewFileId").isNull() ? null : data.path("previewFileId").asLong(),
                data.path("fileSize").asLong(),
                data.path("pageCount").isNull() ? null : data.path("pageCount").asInt(),
                data.path("previewUrl").isNull() ? null : data.path("previewUrl").asText());
    }

    /**
     * POST /api/admin/works/{workId}/editions — 02 §5-2.
     * @param pdf null 이면 파일 없는 판본(정보만)
     * @param pageCount null 이면 업로드된 파일의 쪽수를 쓴다
     * @param overrides 기본 본문 위에 덮어쓸 필드(예: scope=MOVEMENT, movementNumber=2)
     */
    protected long createEdition(Tokens admin, long workId, UploadedPdf pdf, Integer pageCount,
                                 String koreaCopyright, Map<String, Object> overrides) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("fileId", pdf == null ? null : pdf.fileId());
        body.put("previewFileId", pdf == null ? null : pdf.previewFileId());
        body.put("kind", "COMPLETE_SCORE");
        body.put("scope", "COMPLETE");
        body.put("movementNumber", null);
        body.put("pageCount", pageCount);
        body.put("publisher", "Test Verlag");
        body.put("publishYear", 1900);
        body.put("plateNumber", "T.V. 1");
        body.put("editor", "Test Editor");
        body.put("arranger", null);
        body.put("scanner", null);
        body.put("imslpFileUrl", "https://imslp.org/wiki/Special:ImagefromIndex/00001");
        body.put("imslpCopyrightText", "Public Domain");
        body.put("koreaCopyright", koreaCopyright);
        body.put("copyrightNote", "UNKNOWN".equals(koreaCopyright) ? null : "테스트 판정 근거");
        body.put("ccLicenseName", null);
        body.put("ccAttribution", null);
        if (overrides != null) {
            body.putAll(overrides);
        }
        MvcResult result = mockMvc.perform(post("/api/admin/works/{workId}/editions", workId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("id").asLong();
    }

    /**
     * PUT /api/admin/works/{workId}/recommended-edition — 02 §5-6.
     *
     * <p><b>2026-09-21 개정 대응</b>: 사유가 필수가 되면서({@code AdminApiTestSupport.recommendBody} 와 같은 대응)
     * 이 공개 API 픽스처도 기본 사유를 함께 보낸다 — 이 픽스처 자체는 "사유" 를 시험하지 않는다
     * (그건 {@code RecommendReasonIntegrationTest} 의 일이다), 그저 판본을 추천으로 세우는 전제 조건일 뿐이다.
     */
    protected void recommendEdition(Tokens admin, long workId, long editionId) throws Exception {
        mockMvc.perform(put("/api/admin/works/{workId}/recommended-edition", workId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"editionId\": " + editionId + ", \"reason\": \"BETTER_READABILITY\"}"))
                .andExpect(status().isOk());
    }

    /**
     * 곡 생성 → sample.pdf 업로드 → 판본 생성(파일 연결) → 추천 지정.
     * koreaCopyright=FREE 면 곡 상태 READY, RESTRICTED → RESTRICTED, UNKNOWN → UNKNOWN.
     */
    protected ReadyWork createWorkWithRecommendedEdition(Tokens admin, long composerId, String titleKo,
                                                         String titleOriginal, List<String> catalogNumbers,
                                                         List<String> aliases, String level,
                                                         int pageCount, String koreaCopyright) throws Exception {
        long workId = createWork(admin, composerId, titleKo, titleOriginal, catalogNumbers, aliases, level, false);
        UploadedPdf pdf = uploadSamplePdf(admin);
        long editionId = createEdition(admin, workId, pdf, pageCount, koreaCopyright, null);
        recommendEdition(admin, workId, editionId);
        return new ReadyWork(workId, editionId, pdf);
    }

    protected ReadyWork createReadyWork(Tokens admin, long composerId, String titleKo, String titleOriginal,
                                        int pageCount) throws Exception {
        return createWorkWithRecommendedEdition(admin, composerId, titleKo, titleOriginal,
                List.of(), List.of(), "INTERMEDIATE", pageCount, "FREE");
    }
}
