package com.test.test.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.test.test.integration.support.CrawlTestSupport;
import com.test.test.integration.support.FakeImslpClient;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 수집 API 와 워커 동작 계약 (02_API_명세서 §6 전부, 03_기술결정 §3) — TDD Red.
 *
 * <p>워커는 {@link FakeImslpClient} 픽스처로 돈다(실제 IMSLP 없음). 비동기이므로 {@code awaitJob*} 폴링(최대 15초)으로 기다린다.
 * 격리 DB·시드 없음 — 월광은 CREATE 모드가 된다.
 *
 * <p>월광 픽스처 기대값(FakeImslpClient Javadoc): 판본 8개, 파일 상위 2개 = #32718(77476회) → #00014(56391회).
 * 엘리제: 판본 6개(.zip 1개·Sketches 탭 1개 제외), 파일 = #103834 → #05929.
 */
class CrawlApiIntegrationTest extends CrawlTestSupport {

    private static final String MOONLIGHT = FakeImslpClient.MOONLIGHT_URL;
    private static final String ELISE = FakeImslpClient.ELISE_URL;
    private static final String SYMPHONY5 = FakeImslpClient.SYMPHONY5_URL;
    private static final String MISSING_PAGE = "https://imslp.org/wiki/Nonexistent_Piece_(Nobody,_Anon)";
    /** 피아노 독주곡이지만 파일이 .zip 하나뿐 = PDF 판본 0개 (픽스처 nopdf.*) */
    private static final String NO_PDF_PAGE = "https://imslp.org/wiki/Piano_Piece_Without_PDF,_WoO_99_(Beethoven,_Ludwig_van)";

    // ===== §6-1 주소 확인 =====

    @Test
    void check_classifies_urls_and_normalizes_percent_encoding_and_non_ascii() throws Exception {
        Tokens admin = loginAdmin();
        // ATTACH 대상: 관리자가 직접 등록한 곡(수집 판본 없음)
        long composerId = createComposer(admin, composerBody("테스트쇼팽", "Testchopin, Frédéric"));
        Map<String, Object> seeded = workBody(composerId, "녹턴", "Nocturnes, Op.9");
        seeded.put("imslpUrl", "https://imslp.org/wiki/Nocturnes,_Op.9_(Testchopin,_Frédéric)");
        long seededWorkId = createWork(admin, seeded);

        List<String> urls = List.of(
                MOONLIGHT,                                                                                  // 1 NEW
                "https://example.com/foo",                                                                  // 2 INVALID_URL
                "https://imslp.org/wiki/Piano_Sonata_No.14%2C_Op.27_No.2_(Beethoven%2C_Ludwig_van)",        // 3 DUPLICATE of 1
                "https://www.imslp.org/wiki/F%C3%BCr_Elise,_WoO_59_(Beethoven,_Ludwig_van)?x=1#tabScore1",  // 4 NEW (정규화)
                "  " + ELISE + "  ",                                                                         // 5 DUPLICATE of 4
                "https://imslp.org/wiki/Category:Beethoven,_Ludwig_van",                                    // 6 INVALID_URL
                "https://imslp.org/wiki/Nocturnes,_Op.9_(Testchopin,_Fr%C3%A9d%C3%A9ric)",                  // 7 ATTACH
                "");                                                                                        // 빈 줄 무시

        JsonNode data = data(adminPost(admin, "/api/admin/crawl/check", json("urls", urls, "fetchFiles", true))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true)));

        JsonNode items = data.path("items");
        assertThat(items).hasSize(7);
        for (int i = 0; i < 7; i++) {
            assertThat(items.get(i).path("seq").asInt()).isEqualTo(i + 1);
        }
        assertItem(items.get(0), "NEW", MOONLIGHT, null, null);
        assertItem(items.get(1), "INVALID_URL", null, null, null);
        assertItem(items.get(2), "DUPLICATE", MOONLIGHT, null, 1);
        assertItem(items.get(3), "NEW", ELISE, null, null);
        assertItem(items.get(4), "DUPLICATE", ELISE, null, 4);
        assertItem(items.get(5), "INVALID_URL", null, null, null);
        assertItem(items.get(6), "ATTACH", "https://imslp.org/wiki/Nocturnes,_Op.9_(Testchopin,_Frédéric)", seededWorkId, null);
        assertThat(items.get(4).path("inputUrl").asText()).isEqualTo(ELISE);   // 앞뒤 공백 제거

        JsonNode summary = data.path("summary");
        assertThat(summary.path("newCount").asInt()).isEqualTo(2);
        assertThat(summary.path("attachCount").asInt()).isEqualTo(1);
        assertThat(summary.path("existsCount").asInt()).isZero();
        assertThat(summary.path("invalidCount").asInt()).isEqualTo(2);
        assertThat(summary.path("duplicateCount").asInt()).isEqualTo(2);
        assertThat(summary.path("estimatedSeconds").asInt()).isEqualTo(3 * 75);

        JsonNode metaOnly = data(adminPost(admin, "/api/admin/crawl/check", json("urls", urls, "fetchFiles", false))
                .andExpect(status().isOk()));
        assertThat(metaOnly.path("summary").path("estimatedSeconds").asInt()).isEqualTo(3 * 6);
    }

    @Test
    void check_rejects_special_pages_and_non_wiki_paths() throws Exception {
        Tokens admin = loginAdmin();
        List<String> urls = List.of(
                "https://imslp.org/wiki/Special:ImagefromIndex/00014",
                "https://imslp.org/wiki/File:Beethoven,_L.v._-_Piano_Sonata_14.pdf",
                "https://imslp.org/wiki/Template:Foo",
                "https://imslp.org/index.php?title=Foo",
                "ftp://imslp.org/wiki/Foo",
                "not a url at all",
                "http://imslp.org/wiki/Für_Elise,_WoO_59_(Beethoven,_Ludwig_van)");   // http 도 허용, 정규화하면 https

        JsonNode data = data(adminPost(admin, "/api/admin/crawl/check", json("urls", urls)).andExpect(status().isOk()));
        JsonNode items = data.path("items");
        for (int i = 0; i < 6; i++) {
            assertThat(items.get(i).path("verdict").asText()).as("seq " + (i + 1)).isEqualTo("INVALID_URL");
            assertThat(items.get(i).path("canonicalUrl").isNull()).isTrue();
        }
        assertItem(items.get(6), "NEW", ELISE, null, null);
        assertThat(data.path("summary").path("invalidCount").asInt()).isEqualTo(6);
        assertThat(data.path("summary").path("estimatedSeconds").asInt()).isEqualTo(75);   // fetchFiles 기본 true
    }

    @Test
    void check_reports_exists_after_a_crawl_has_attached_imslp_editions() throws Exception {
        Tokens admin = loginAdmin();
        long workId = crawlSingle(admin, MOONLIGHT, false);

        JsonNode data = data(adminPost(admin, "/api/admin/crawl/check", json("urls", List.of(MOONLIGHT))).andExpect(status().isOk()));
        assertItem(data.path("items").get(0), "EXISTS", MOONLIGHT, workId, null);
        assertThat(data.path("summary").path("existsCount").asInt()).isEqualTo(1);
        assertThat(data.path("summary").path("estimatedSeconds").asInt()).isZero();
    }

    @Test
    void check_validation_requires_urls() throws Exception {
        Tokens admin = loginAdmin();
        adminPost(admin, "/api/admin/crawl/check", json("urls", List.of()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[?(@.field == 'urls')]").exists());
        adminPost(admin, "/api/admin/crawl/check", json("urls", null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
    }

    // ===== §6-2 작업 생성 =====

    @Test
    void start_job_returns_201_running_dto_and_worker_processes_moonlight_end_to_end() throws Exception {
        Tokens admin = loginAdmin();
        fakeImslp.holdFiles();   // 파일 단계에서 붙잡아 RUNNING 상태를 관찰

        JsonNode created = startJob(admin, true, MOONLIGHT);
        long jobId = created.path("id").asLong();
        assertThat(jobId).isPositive();
        assertThat(created.path("status").asText()).isEqualTo("RUNNING");
        assertThat(created.path("stopRequested").asBoolean()).isFalse();
        assertThat(created.path("stoppedByRestart").asBoolean()).isFalse();
        assertThat(created.path("fetchFiles").asBoolean()).isTrue();
        assertThat(created.path("totalCount").asInt()).isEqualTo(1);
        assertThat(created.path("processedCount").asInt()).isZero();
        assertThat(created.path("pendingCount").asInt()).isEqualTo(1);
        assertThat(created.path("estimatedRemainingSeconds").asInt()).isEqualTo(75);   // 처리된 게 없으면 항목당 75초
        assertThat(created.path("createdBy").asText()).isEqualTo(ADMIN_USERNAME);
        assertThat(created.path("createdAt").isTextual()).isTrue();
        assertThat(created.path("finishedAt").isNull()).isTrue();
        assertThat(created.path("retryOfJobId").isNull()).isTrue();
        assertThat(created.path("failureReason").isNull()).isTrue();

        // 파일 단계 진입: currentItem / currentStage / currentFileIndex
        JsonNode downloading = awaitJob(admin, jobId,
                j -> "DOWNLOADING_FILE".equals(j.path("currentStage").asText()), "currentStage=DOWNLOADING_FILE");
        assertThat(downloading.path("currentItem").path("seq").asInt()).isEqualTo(1);
        assertThat(downloading.path("currentItem").path("url").asText()).isEqualTo(MOONLIGHT);
        assertThat(downloading.path("currentItem").path("title").asText()).isEqualTo("Piano Sonata No.14, Op.27 No.2");
        assertThat(downloading.path("currentFileIndex").asInt()).isEqualTo(1);
        assertThat(downloading.path("currentFileTotal").asInt()).isEqualTo(2);
        assertThat(downloading.path("startedAt").isTextual()).isTrue();
        assertThat(downloading.path("elapsedSeconds").asLong()).isGreaterThanOrEqualTo(0);
        assertThat(item(downloading, 1).path("status").asText()).isEqualTo("PROCESSING");

        adminGet(admin, "/api/admin/crawl/jobs/active")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(jobId))
                .andExpect(jsonPath("$.data.status").value("RUNNING"));

        fakeImslp.releaseFiles();
        JsonNode done = awaitJobStatus(admin, jobId, "COMPLETED");
        assertThat(done.path("processedCount").asInt()).isEqualTo(1);
        assertThat(done.path("successCount").asInt()).isEqualTo(1);
        assertThat(done.path("failCount").asInt()).isZero();
        assertThat(done.path("skipCount").asInt()).isZero();
        assertThat(done.path("hiddenCount").asInt()).isZero();
        assertThat(done.path("pendingCount").asInt()).isZero();
        assertThat(done.path("currentItem").isNull()).isTrue();
        assertThat(done.path("currentStage").isNull()).isTrue();
        assertThat(done.path("estimatedRemainingSeconds").isNull()).isTrue();
        assertThat(done.path("finishedAt").isTextual()).isTrue();

        JsonNode item = item(done, 1);
        assertThat(item.path("id").asLong()).isPositive();
        assertThat(item.path("url").asText()).isEqualTo(MOONLIGHT);
        assertThat(item.path("mode").asText()).isEqualTo("CREATE");
        assertThat(item.path("status").asText()).isEqualTo("SUCCESS");
        assertThat(item.path("failReason").isNull()).isTrue();
        assertThat(item.path("message").asText()).isEqualTo("판본 8개, 파일 2개 받음");
        assertThat(item.path("workTitle").asText()).isEqualTo("Piano Sonata No.14, Op.27 No.2");
        assertThat(item.path("editionCount").asInt()).isEqualTo(8);
        assertThat(item.path("fileCount").asInt()).isEqualTo(2);
        assertThat(item.path("startedAt").isTextual()).isTrue();
        assertThat(item.path("finishedAt").isTextual()).isTrue();
        long workId = item.path("workId").asLong();

        // 요청 예산: HTML 1 + 위키텍스트 1, 파일은 다운로드 수 상위 2개만, 상위 순서대로
        assertThat(fakeImslp.getWorkPageRequests()).isEqualTo(1);
        assertThat(fakeImslp.getWikitextRequests()).isEqualTo(1);
        assertThat(fakeImslp.getDownloadedFileIds()).containsExactly("32718", "00014");

        adminGet(admin, "/api/admin/crawl/jobs/active")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(org.hamcrest.Matchers.nullValue()));

        // ── 곡 (AdminWorkDetailDTO) ──
        JsonNode work = getWork(admin, workId);
        assertThat(work.path("titleKo").isNull()).isTrue();
        assertThat(work.path("titleOriginal").asText()).isEqualTo("Piano Sonata No.14, Op.27 No.2");
        assertThat(work.path("composer").path("nameOriginal").asText()).isEqualTo("Beethoven, Ludwig van");
        assertThat(work.path("composer").path("nameKo").isNull()).isTrue();
        assertThat(work.path("composer").path("nameKoMissing").asBoolean()).isTrue();
        assertThat(strings(work.path("catalogNumbers"))).containsExactly("Op.27 No.2");
        assertThat(strings(work.path("aliases"))).contains("피아노 소나타 14번", "Moonlight Sonata");
        assertThat(work.path("level").isNull()).isTrue();
        assertThat(work.path("compositionYear").asText()).isEqualTo("1802");
        assertThat(work.path("musicalKey").asText()).isEqualTo("C-sharp minor");
        assertThat(work.path("movements").asText()).startsWith("3 movements");
        assertThat(work.path("imslpUrl").asText()).isEqualTo(MOONLIGHT);
        assertThat(work.path("hidden").asBoolean()).isFalse();
        assertThat(work.path("status").asText()).isEqualTo("PREPARING");
        assertThat(work.path("needsWork").asBoolean()).isTrue();
        assertThat(strings(work.path("missing"))).containsExactlyInAnyOrder("TITLE_KO", "LEVEL", "RECOMMENDED_EDITION");
        assertThat(work.path("recommendedEditionId").isNull()).isTrue();
        assertThat(work.path("editions")).hasSize(8);

        // ── 판본 ──
        JsonNode top = editionByImslpFileId(work, "32718");
        assertThat(top.path("kind").asText()).isEqualTo("COMPLETE_SCORE");
        assertThat(top.path("scope").asText()).isEqualTo("COMPLETE");
        assertThat(top.path("hasFile").asBoolean()).isTrue();
        assertThat(top.path("fileSize").asLong()).isEqualTo(samplePdfBytes().length);
        assertThat(top.path("pageCount").asInt()).isEqualTo(FakeImslpClient.SAMPLE_PDF_PAGE_COUNT);   // 파일을 받았으면 PDFBox 값
        assertThat(top.path("previewUrl").asText()).startsWith("/uploads/").endsWith(".png");
        assertThat(top.path("imslpDownloadCount").asInt()).isEqualTo(77476);
        assertThat(top.path("imslpLicenseCode").asText()).isEqualTo("PD");
        assertThat(top.path("editor").asText()).contains("Köhler");
        assertThat(top.path("isCandidate").asBoolean()).isTrue();
        assertThat(top.path("fileFetchedAt").isTextual()).isTrue();
        assertThat(work.path("candidateEditionId").asLong()).isEqualTo(top.path("id").asLong());
        assertThat(work.path("editions").get(0).path("id").asLong()).isEqualTo(top.path("id").asLong());   // 후보가 맨 앞
        mockMvc.perform(get(top.path("previewUrl").asText())).andExpect(status().isOk());

        JsonNode schenker = editionByImslpFileId(work, "00014");
        assertThat(schenker.path("hasFile").asBoolean()).isTrue();
        assertThat(schenker.path("isCandidate").asBoolean()).isFalse();
        assertThat(schenker.path("imslpFileUrl").asText()).isEqualTo("https://imslp.org/wiki/Special:ImagefromIndex/00014");
        assertThat(schenker.path("imslpOriginalFileName").asText()).isEqualTo("Beethoven, L.v. - Piano Sonata 14.pdf");
        assertThat(schenker.path("imslpDescription").asText()).isEqualTo("Complete Score");
        assertThat(schenker.path("imslpDownloadCount").asInt()).isEqualTo(56391);
        assertThat(schenker.path("imslpCopyrightText").asText()).isEqualTo("Public Domain");
        assertThat(schenker.path("editor").asText()).isEqualTo("Heinrich Schenker");
        assertThat(schenker.path("publisher").asText()).contains("Universal Edition");
        assertThat(schenker.path("publishYear").asInt()).isEqualTo(1921);
        assertThat(schenker.path("plateNumber").asText()).isEqualTo("U.E. 7000");
        assertThat(schenker.path("scanner").asText()).isEqualTo("Unknown");
        assertThat(schenker.path("koreaCopyright").asText()).isEqualTo("UNKNOWN");
        assertThat(schenker.path("copyrightNote").isNull()).isTrue();

        JsonNode third = editionByImslpFileId(work, "15808");   // 3위 → 파일 없음, 쪽수는 HTML 값
        assertThat(third.path("hasFile").asBoolean()).isFalse();
        assertThat(third.path("pageCount").asInt()).isEqualTo(16);
        assertThat(third.path("fileSize").isNull()).isTrue();
        assertThat(third.path("previewUrl").isNull()).isTrue();

        JsonNode movement = editionByImslpFileId(work, "218185");   // 악장별 — 다운로드 수가 높아도 파일 대상 아님
        assertThat(movement.path("scope").asText()).isEqualTo("MOVEMENT");
        assertThat(movement.path("movementNumber").asInt()).isEqualTo(1);
        assertThat(movement.path("sectionLabel").asText()).isEqualTo("Adagio sostenuto (No.1)");
        assertThat(movement.path("imslpLicenseCode").asText()).isEqualTo("CC_BY");
        assertThat(movement.path("hasFile").asBoolean()).isFalse();

        JsonNode arrangement = editionByImslpFileId(work, "342167");
        assertThat(arrangement.path("kind").asText()).isEqualTo("ARRANGEMENT");
        assertThat(arrangement.path("scope").asText()).isEqualTo("COMPLETE");
        assertThat(arrangement.path("arranger").asText()).isEqualTo("Jakub Kowalewski");
        assertThat(arrangement.path("hasFile").asBoolean()).isFalse();

        assertThat(editionByImslpFileId(work, "926972").path("imslpLicenseCode").asText()).isEqualTo("CC_BY_SA");
        assertThat(editionByImslpFileId(work, "994719").path("hasFile").asBoolean()).isFalse();   // 같은 블록의 2번째 파일도 판본

        // 목록/상세/대시보드 연동
        adminGet(admin, "/api/admin/crawl/jobs")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].id").value(jobId))
                .andExpect(jsonPath("$.data.content[0].status").value("COMPLETED"));
        adminGet(admin, "/api/admin/dashboard")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.latestJob.id").value(jobId))
                .andExpect(jsonPath("$.data.activeJob").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void fetch_files_false_saves_editions_without_files() throws Exception {
        Tokens admin = loginAdmin();
        long jobId = startJob(admin, false, MOONLIGHT).path("id").asLong();
        JsonNode done = awaitJobStatus(admin, jobId, "COMPLETED");
        assertThat(done.path("fetchFiles").asBoolean()).isFalse();

        JsonNode item = item(done, 1);
        assertThat(item.path("status").asText()).isEqualTo("SUCCESS");
        assertThat(item.path("message").asText()).isEqualTo("판본 8개, 파일 0개 받음");
        assertThat(item.path("fileCount").asInt()).isZero();
        assertThat(fakeImslp.getDownloadedFileIds()).isEmpty();

        JsonNode work = getWork(admin, item.path("workId").asLong());
        assertThat(work.path("editions")).hasSize(8);
        for (JsonNode edition : work.path("editions")) {
            assertThat(edition.path("hasFile").asBoolean()).isFalse();
        }
        assertThat(work.path("candidateEditionId").isNull()).isTrue();
        assertThat(editionByImslpFileId(work, "32718").path("pageCount").asInt()).isEqualTo(14);   // HTML 값
    }

    @Test
    void two_items_share_the_upserted_composer_and_are_processed_in_seq_order() throws Exception {
        Tokens admin = loginAdmin();
        long jobId = startJob(admin, true, MOONLIGHT, ELISE).path("id").asLong();
        JsonNode done = awaitJobStatus(admin, jobId, "COMPLETED");
        assertThat(done.path("totalCount").asInt()).isEqualTo(2);
        assertThat(done.path("successCount").asInt()).isEqualTo(2);

        JsonNode elise = item(done, 2);
        assertThat(elise.path("message").asText()).isEqualTo("판본 6개, 파일 2개 받음");
        assertThat(elise.path("workTitle").asText()).isEqualTo("Für Elise, WoO 59");
        assertThat(fakeImslp.getDownloadedFileIds()).containsExactly("32718", "00014", "103834", "05929");

        JsonNode moonlightWork = getWork(admin, item(done, 1).path("workId").asLong());
        JsonNode eliseWork = getWork(admin, elise.path("workId").asLong());
        assertThat(eliseWork.path("composer").path("id").asLong()).isEqualTo(moonlightWork.path("composer").path("id").asLong());
        assertThat(eliseWork.path("titleOriginal").asText()).isEqualTo("Für Elise, WoO 59");
        assertThat(strings(eliseWork.path("catalogNumbers"))).containsExactly("WoO 59");
        assertThat(strings(eliseWork.path("aliases"))).contains("엘리제를 위하여");
        assertThat(eliseWork.path("musicalKey").asText()).isEqualTo("A minor");
        assertThat(eliseWork.path("compositionYear").asText()).isEqualTo("1810");
        assertThat(eliseWork.path("editions")).hasSize(6);
        JsonNode nohl = editionByImslpFileId(eliseWork, "103834");
        assertThat(nohl.path("scope").asText()).isEqualTo("COMPLETE");     // h4 헤딩 없는 단악장 작품 = 전곡
        assertThat(nohl.path("sectionLabel").isNull()).isTrue();
        assertThat(nohl.path("editor").asText()).contains("Nohl");
        assertThat(nohl.path("hasFile").asBoolean()).isTrue();
        assertThat(editionByImslpFileId(eliseWork, "103779").path("imslpLicenseCode").asText()).isEqualTo("CC_BY_NC_SA");
        assertThat(editionByImslpFileId(eliseWork, "103779").path("hasFile").asBoolean()).isFalse();   // NC 는 받지 않음
        assertThat(editionByImslpFileId(eliseWork, "11471").path("hasFile").asBoolean()).isFalse();    // 3위
        for (JsonNode edition : eliseWork.path("editions")) {
            assertThat(edition.path("imslpFileId").asText()).isNotIn("219532", "969424");   // .zip / Sketches 탭 제외
        }
    }

    @Test
    void non_piano_work_is_created_hidden_without_files() throws Exception {
        Tokens admin = loginAdmin();
        long jobId = startJob(admin, true, SYMPHONY5).path("id").asLong();
        JsonNode done = awaitJobStatus(admin, jobId, "COMPLETED");
        assertThat(done.path("hiddenCount").asInt()).isEqualTo(1);
        assertThat(done.path("successCount").asInt()).isZero();
        assertThat(done.path("processedCount").asInt()).isEqualTo(1);

        JsonNode item = item(done, 1);
        assertThat(item.path("status").asText()).isEqualTo("HIDDEN");
        assertThat(item.path("message").asText()).isEqualTo("피아노 독주곡이 아닌 것 같아요");
        assertThat(item.path("fileCount").asInt()).isZero();
        assertThat(fakeImslp.getDownloadedFileIds()).isEmpty();

        long workId = item.path("workId").asLong();
        JsonNode work = getWork(admin, workId);
        assertThat(work.path("titleOriginal").asText()).isEqualTo("Symphony No.5, Op.67");
        assertThat(work.path("hidden").asBoolean()).isTrue();
        assertThat(work.path("hiddenReason").asText()).isEqualTo("NOT_PIANO_SOLO");
        // 사용자 화면에서는 없는 곡
        mockMvc.perform(get("/api/works/{id}", workId)).andExpect(status().isNotFound());
    }

    /**
     * 02 §6-6 — PDF 판본이 하나도 없는 작품 페이지는 항목이 FAILED/NO_PDF_EDITION 이어야 한다.
     * (고정 문구 "작품 페이지에 PDF 판본이 없어요" 는 qa 대조용이다.)
     */
    @Test
    void work_page_without_any_pdf_edition_fails_with_no_pdf_edition() throws Exception {
        fakeImslp.registerFixture(NO_PDF_PAGE, "nopdf");
        Tokens admin = loginAdmin();
        long jobId = startJob(admin, true, NO_PDF_PAGE).path("id").asLong();
        JsonNode done = awaitJobStatus(admin, jobId, "COMPLETED");
        assertThat(done.path("failCount").asInt()).isEqualTo(1);
        assertThat(done.path("successCount").asInt()).isZero();

        JsonNode item = item(done, 1);
        assertThat(item.path("status").asText()).isEqualTo("FAILED");
        assertThat(item.path("failReason").asText()).isEqualTo("NO_PDF_EDITION");
        assertThat(item.path("message").asText()).isEqualTo("작품 페이지에 PDF 판본이 없어요");
        assertThat(item.path("editionCount").asInt()).isZero();
        assertThat(fakeImslp.getDownloadedFileIds()).isEmpty();
    }

    @Test
    void page_not_found_fails_only_that_item_and_the_rest_continues() throws Exception {
        Tokens admin = loginAdmin();
        long jobId = startJob(admin, false, MISSING_PAGE, MOONLIGHT).path("id").asLong();
        JsonNode done = awaitJobStatus(admin, jobId, "COMPLETED");
        assertThat(done.path("failCount").asInt()).isEqualTo(1);
        assertThat(done.path("successCount").asInt()).isEqualTo(1);
        assertThat(done.path("processedCount").asInt()).isEqualTo(2);

        JsonNode failed = item(done, 1);
        assertThat(failed.path("status").asText()).isEqualTo("FAILED");
        assertThat(failed.path("failReason").asText()).isEqualTo("PAGE_NOT_FOUND");
        assertThat(failed.path("message").asText()).isEqualTo("IMSLP에 그 페이지가 없어요");
        assertThat(failed.path("workId").isNull()).isTrue();
        assertThat(item(done, 2).path("status").asText()).isEqualTo("SUCCESS");
    }

    /**
     * 메타 읽기 단계의 <b>예상 밖 오류</b>(우리 쪽 버그·파싱 사고)는 {@code INTERNAL_ERROR} 다 — 02 §6-10(2026-09-07).
     *
     * <p>{@code IMSLP_UNAVAILABLE} 로 뭉개면 두 가지가 잘못된다: 관리자가 IMSLP 탓으로 읽고(고칠 사람은 우리다),
     * 연속 무응답으로 세어져 멀쩡한 IMSLP 를 두고 작업이 10분 멈춘다.
     * 여기서는 <b>한 항목만</b> 깨뜨리므로 작업은 PAUSED 없이 끝까지 가야 한다.
     */
    @Test
    void unexpected_error_while_reading_metadata_is_internal_error_and_does_not_pause() throws Exception {
        Tokens admin = loginAdmin();
        fakeImslp.markBroken(ELISE);

        long jobId = startJob(admin, false, ELISE, MOONLIGHT).path("id").asLong();
        JsonNode done = awaitJobStatus(admin, jobId, "COMPLETED");

        JsonNode failed = item(done, 1);
        assertThat(failed.path("status").asText()).isEqualTo("FAILED");
        assertThat(failed.path("failReason").asText()).isEqualTo("INTERNAL_ERROR");
        assertThat(failed.path("message").asText()).isEqualTo("처리 중 오류가 났어요");
        assertThat(item(done, 2).path("status").asText()).isEqualTo("SUCCESS");
        assertThat(done.path("pausedUntil").isNull()).isTrue();
    }

    @Test
    void corrupt_file_marks_item_failed_but_keeps_editions_and_other_file() throws Exception {
        Tokens admin = loginAdmin();
        fakeImslp.markFileCorrupt("32718");

        long jobId = startJob(admin, true, MOONLIGHT).path("id").asLong();
        JsonNode done = awaitJobStatus(admin, jobId, "COMPLETED");
        JsonNode item = item(done, 1);
        assertThat(item.path("status").asText()).isEqualTo("FAILED");
        assertThat(item.path("failReason").asText()).isEqualTo("FILE_DOWNLOAD_FAILED");
        assertThat(item.path("message").asText()).isEqualTo("파일을 받다가 끊겼어요");
        assertThat(item.path("editionCount").asInt()).isEqualTo(8);
        assertThat(item.path("fileCount").asInt()).isEqualTo(1);

        JsonNode work = getWork(admin, item.path("workId").asLong());
        assertThat(work.path("editions")).hasSize(8);
        assertThat(editionByImslpFileId(work, "32718").path("hasFile").asBoolean()).isFalse();
        assertThat(editionByImslpFileId(work, "00014").path("hasFile").asBoolean()).isTrue();
    }

    @Test
    void start_job_rejects_when_nothing_to_process_or_another_job_is_active() throws Exception {
        Tokens admin = loginAdmin();

        postJob(admin, jobBody(true, "https://example.com/foo", "https://imslp.org/wiki/Category:Nope"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_RULE_VIOLATION"))
                .andExpect(jsonPath("$.message").value("수집할 주소가 없어요"));

        postJob(admin, jobBody(true, List.of()))
                .andExpect(status().isBadRequest());

        fakeImslp.holdFiles();
        long jobId = startJob(admin, true, MOONLIGHT).path("id").asLong();
        awaitJob(admin, jobId, j -> "DOWNLOADING_FILE".equals(j.path("currentStage").asText()), "파일 단계");

        postJob(admin, jobBody(true, ELISE))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("DUPLICATE_RESOURCE"))
                .andExpect(jsonPath("$.message").value("진행 중인 수집이 있어요"));

        fakeImslp.releaseFiles();
        awaitJobStatus(admin, jobId, "COMPLETED");
    }

    @Test
    void exists_items_are_skipped_unless_refresh_and_refresh_never_refetches_files() throws Exception {
        Tokens admin = loginAdmin();
        long workId = crawlSingle(admin, MOONLIGHT, true);
        assertThat(fakeImslp.getDownloadedFileIds()).hasSize(2);

        // EXISTS + refresh=false 만 있으면 처리할 항목 0개
        postJob(admin, jobBody(true, List.of(item(MOONLIGHT, false))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("수집할 주소가 없어요"));

        // SKIP 항목은 만들어지되 즉시 SKIPPED, REFRESH 는 파일 재요청 없이 정보만 갱신
        long jobId = data(postJob(admin, jobBody(true, List.of(item(MOONLIGHT, false), item(ELISE, false))))
                .andExpect(status().isCreated())).path("id").asLong();
        JsonNode done = awaitJobStatus(admin, jobId, "COMPLETED");
        assertThat(done.path("skipCount").asInt()).isEqualTo(1);
        assertThat(done.path("successCount").asInt()).isEqualTo(1);
        JsonNode skipped = item(done, 1);
        assertThat(skipped.path("mode").asText()).isEqualTo("SKIP");
        assertThat(skipped.path("status").asText()).isEqualTo("SKIPPED");
        assertThat(skipped.path("message").asText()).isEqualTo("이미 있음 — 건너뜀");
        assertThat(skipped.path("workId").asLong()).isEqualTo(workId);

        long refreshJobId = data(postJob(admin, jobBody(true, List.of(item(MOONLIGHT, true))))
                .andExpect(status().isCreated())).path("id").asLong();
        JsonNode refreshed = awaitJobStatus(admin, refreshJobId, "COMPLETED");
        JsonNode refreshItem = item(refreshed, 1);
        assertThat(refreshItem.path("mode").asText()).isEqualTo("REFRESH");
        assertThat(refreshItem.path("status").asText()).isEqualTo("SUCCESS");
        assertThat(refreshItem.path("message").asText()).isEqualTo("판본 정보 갱신 (파일 재요청 없음)");
        assertThat(refreshItem.path("workId").asLong()).isEqualTo(workId);
        assertThat(fakeImslp.getDownloadedFileIds()).hasSize(4);   // 첫 수집 2 + 엘리제 2, 월광 재요청 없음

        JsonNode work = getWork(admin, workId);
        assertThat(work.path("editions")).hasSize(8);   // 중복 생성 없음
        assertThat(editionByImslpFileId(work, "32718").path("hasFile").asBoolean()).isTrue();
    }

    @Test
    void attach_mode_adds_editions_to_admin_registered_work_and_keeps_korean_fields() throws Exception {
        Tokens admin = loginAdmin();
        long composerId = createComposer(admin, composerBody("베토벤", "Beethoven, Ludwig van"));
        Map<String, Object> body = workBody(composerId, "월광 소나타", "Piano Sonata No.14, Op.27 No.2");
        body.put("aliases", List.of("월광"));
        body.put("imslpUrl", MOONLIGHT);
        long workId = createWork(admin, body);

        long jobId = startJob(admin, true, MOONLIGHT).path("id").asLong();
        JsonNode done = awaitJobStatus(admin, jobId, "COMPLETED");
        JsonNode item = item(done, 1);
        assertThat(item.path("mode").asText()).isEqualTo("ATTACH");
        assertThat(item.path("status").asText()).isEqualTo("SUCCESS");
        assertThat(item.path("workId").asLong()).isEqualTo(workId);

        JsonNode work = getWork(admin, workId);
        assertThat(work.path("titleKo").asText()).isEqualTo("월광 소나타");   // 관리자 입력 보존
        assertThat(work.path("level").asText()).isEqualTo("INTERMEDIATE");
        assertThat(work.path("composer").path("id").asLong()).isEqualTo(composerId);
        assertThat(work.path("composer").path("nameKo").asText()).isEqualTo("베토벤");
        assertThat(strings(work.path("aliases"))).contains("월광", "피아노 소나타 14번");   // 기존 + IMSLP 별칭
        assertThat(work.path("editions")).hasSize(8);
        assertThat(strings(work.path("missing"))).containsExactly("RECOMMENDED_EDITION");
    }

    // ===== §6-7 ~ 6-9 중지 / 재개 / 재시도 =====

    @Test
    void stop_finishes_current_item_then_stops_and_resume_continues_from_pending() throws Exception {
        Tokens admin = loginAdmin();
        fakeImslp.holdFiles();
        long jobId = startJob(admin, true, MOONLIGHT, ELISE).path("id").asLong();
        awaitJob(admin, jobId, j -> "DOWNLOADING_FILE".equals(j.path("currentStage").asText()), "파일 단계");

        adminPost(admin, "/api/admin/crawl/jobs/{id}/stop", json(), jobId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(jobId))
                .andExpect(jsonPath("$.data.status").value("RUNNING"))
                .andExpect(jsonPath("$.data.stopRequested").value(true));

        fakeImslp.releaseFiles();
        JsonNode stopped = awaitJobStatus(admin, jobId, "STOPPED");
        assertThat(item(stopped, 1).path("status").asText()).isEqualTo("SUCCESS");
        assertThat(item(stopped, 2).path("status").asText()).isEqualTo("PENDING");
        assertThat(stopped.path("pendingCount").asInt()).isEqualTo(1);
        assertThat(stopped.path("finishedAt").isTextual()).isTrue();
        assertThat(stopped.path("stoppedByRestart").asBoolean()).isFalse();
        adminGet(admin, "/api/admin/crawl/jobs/active")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(org.hamcrest.Matchers.nullValue()));

        adminPost(admin, "/api/admin/crawl/jobs/{id}/stop", json(), jobId)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_RULE_VIOLATION"))
                .andExpect(jsonPath("$.message").value("중지할 수 있는 상태가 아니에요"));

        adminPost(admin, "/api/admin/crawl/jobs/{id}/resume", json(), jobId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RUNNING"))
                .andExpect(jsonPath("$.data.stopRequested").value(false));
        JsonNode done = awaitJobStatus(admin, jobId, "COMPLETED");
        assertThat(item(done, 2).path("status").asText()).isEqualTo("SUCCESS");
        assertThat(done.path("successCount").asInt()).isEqualTo(2);
        assertThat(fakeImslp.getDownloadedFileIds()).containsExactly("32718", "00014", "103834", "05929");   // 받은 파일은 다시 안 받음

        adminPost(admin, "/api/admin/crawl/jobs/{id}/resume", json(), jobId)
                .andExpect(status().isBadRequest());   // 대기 항목 0개
        adminPost(admin, "/api/admin/crawl/jobs/{id}/resume", json(), 99_999_999L)
                .andExpect(status().isNotFound());
        adminGet(admin, "/api/admin/crawl/jobs/{id}", 99_999_999L)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("NOT_FOUND"));
    }

    @Test
    void three_consecutive_unavailable_items_pause_the_job_and_pause_can_be_stopped_immediately() throws Exception {
        Tokens admin = loginAdmin();
        fakeImslp.setAllUnavailable(true);
        long jobId = startJob(admin, true, MOONLIGHT, ELISE, SYMPHONY5, MISSING_PAGE).path("id").asLong();

        JsonNode paused = awaitJobStatus(admin, jobId, "PAUSED");
        assertThat(paused.path("pausedUntil").isTextual()).isTrue();
        assertThat(paused.path("failCount").asInt()).isEqualTo(3);
        assertThat(paused.path("pendingCount").asInt()).isEqualTo(1);
        assertThat(paused.path("currentItem").isNull()).isTrue();
        for (int seq = 1; seq <= 3; seq++) {
            JsonNode item = item(paused, seq);
            assertThat(item.path("status").asText()).isEqualTo("FAILED");
            assertThat(item.path("failReason").asText()).isEqualTo("IMSLP_UNAVAILABLE");
            assertThat(item.path("message").asText()).isEqualTo("IMSLP가 응답하지 않아요");
        }
        assertThat(item(paused, 4).path("status").asText()).isEqualTo("PENDING");
        adminGet(admin, "/api/admin/crawl/jobs/active")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(jobId))
                .andExpect(jsonPath("$.data.status").value("PAUSED"));

        // PAUSED 의 stop 은 즉시 STOPPED
        adminPost(admin, "/api/admin/crawl/jobs/{id}/stop", json(), jobId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("STOPPED"));
        awaitJobStatus(admin, jobId, "STOPPED");

        // IMSLP 가 돌아오면 이어서 시작 → 남은 1건 처리
        fakeImslp.setAllUnavailable(false);
        adminPost(admin, "/api/admin/crawl/jobs/{id}/resume", json(), jobId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RUNNING"))
                .andExpect(jsonPath("$.data.pausedUntil").value(org.hamcrest.Matchers.nullValue()));
        JsonNode done = awaitJobStatus(admin, jobId, "COMPLETED");
        assertThat(item(done, 4).path("status").asText()).isEqualTo("FAILED");   // MISSING_PAGE 는 404
        assertThat(item(done, 4).path("failReason").asText()).isEqualTo("PAGE_NOT_FOUND");
        assertThat(done.path("failCount").asInt()).isEqualTo(4);
    }

    @Test
    void resume_from_pause_that_fails_three_more_times_stops_with_reason() throws Exception {
        Tokens admin = loginAdmin();
        fakeImslp.setAllUnavailable(true);
        String[] urls = new String[7];
        for (int i = 0; i < 7; i++) {
            urls[i] = "https://imslp.org/wiki/Unreachable_Piece_No." + (i + 1) + "_(Nobody,_Anon)";
        }
        long jobId = startJob(admin, false, urls).path("id").asLong();
        awaitJobStatus(admin, jobId, "PAUSED");

        // 일시 정지 즉시 재개 (여전히 무응답)
        adminPost(admin, "/api/admin/crawl/jobs/{id}/resume", json(), jobId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RUNNING"))
                .andExpect(jsonPath("$.data.pausedUntil").value(org.hamcrest.Matchers.nullValue()));

        JsonNode stopped = awaitJobStatus(admin, jobId, "STOPPED");
        assertThat(stopped.path("failureReason").asText()).isEqualTo("IMSLP가 응답하지 않아요");
        assertThat(stopped.path("failCount").asInt()).isEqualTo(6);
        assertThat(stopped.path("pendingCount").asInt()).isEqualTo(1);
        assertThat(stopped.path("finishedAt").isTextual()).isTrue();
    }

    @Test
    void retry_failed_creates_new_job_with_only_failed_items() throws Exception {
        Tokens admin = loginAdmin();
        fakeImslp.markNotFound(ELISE);
        long original = startJob(admin, false, MOONLIGHT, ELISE, SYMPHONY5).path("id").asLong();
        JsonNode done = awaitJobStatus(admin, original, "COMPLETED");
        assertThat(done.path("successCount").asInt()).isEqualTo(1);
        assertThat(done.path("failCount").asInt()).isEqualTo(1);
        assertThat(done.path("hiddenCount").asInt()).isEqualTo(1);

        fakeImslp.unmarkNotFound(ELISE);
        JsonNode retry = data(adminPost(admin, "/api/admin/crawl/jobs/{id}/retry-failed", json(), original)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true)));
        long retryId = retry.path("id").asLong();
        assertThat(retryId).isNotEqualTo(original);
        assertThat(retry.path("status").asText()).isEqualTo("RUNNING");
        assertThat(retry.path("retryOfJobId").asLong()).isEqualTo(original);
        assertThat(retry.path("fetchFiles").asBoolean()).isFalse();   // 원본 값
        assertThat(retry.path("totalCount").asInt()).isEqualTo(1);    // FAILED 만 (HIDDEN·SUCCESS 제외)

        JsonNode retried = awaitJobStatus(admin, retryId, "COMPLETED");
        JsonNode item = item(retried, 1);
        assertThat(item.path("url").asText()).isEqualTo(ELISE);
        assertThat(item.path("status").asText()).isEqualTo("SUCCESS");
        assertThat(item.path("message").asText()).isEqualTo("판본 6개, 파일 0개 받음");

        // 실패 0건인 작업은 400
        adminPost(admin, "/api/admin/crawl/jobs/{id}/retry-failed", json(), retryId)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_RULE_VIOLATION"));
        adminPost(admin, "/api/admin/crawl/jobs/{id}/retry-failed", json(), 99_999_999L)
                .andExpect(status().isNotFound());

        // 목록은 최근 생성순
        JsonNode list = data(adminGet(admin, "/api/admin/crawl/jobs").andExpect(status().isOk()));
        assertThat(longs(list.path("content"), "id")).startsWith(retryId, original);
    }

    @Test
    void retry_failed_and_resume_are_rejected_while_another_job_is_active() throws Exception {
        Tokens admin = loginAdmin();
        fakeImslp.markNotFound(ELISE);
        long failedJob = startJob(admin, false, ELISE).path("id").asLong();
        awaitJobStatus(admin, failedJob, "COMPLETED");

        fakeImslp.holdFiles();
        long stoppedJob = startJob(admin, true, MOONLIGHT, SYMPHONY5).path("id").asLong();
        awaitJob(admin, stoppedJob, j -> "DOWNLOADING_FILE".equals(j.path("currentStage").asText()), "파일 단계");
        adminPost(admin, "/api/admin/crawl/jobs/{id}/stop", json(), stoppedJob).andExpect(status().isOk());
        fakeImslp.releaseFiles();
        awaitJobStatus(admin, stoppedJob, "STOPPED");

        // 아직 수집 안 된 엘리제로 활성 작업을 만들어 파일 단계에서 붙잡는다 (월광은 위에서 이미 수집돼 EXISTS)
        fakeImslp.unmarkNotFound(ELISE);
        fakeImslp.holdFiles();
        long active = startJob(admin, true, ELISE).path("id").asLong();
        awaitJob(admin, active, j -> "DOWNLOADING_FILE".equals(j.path("currentStage").asText()), "파일 단계");

        adminPost(admin, "/api/admin/crawl/jobs/{id}/retry-failed", json(), failedJob)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("DUPLICATE_RESOURCE"));
        adminPost(admin, "/api/admin/crawl/jobs/{id}/resume", json(), stoppedJob)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("DUPLICATE_RESOURCE"));

        fakeImslp.releaseFiles();
        awaitJob(admin, active, j -> !isActive(j), "종료");
    }

    // ===== helpers =====

    private void assertItem(JsonNode item, String verdict, String canonicalUrl, Long existingWorkId, Integer duplicateOfSeq) {
        assertThat(item.path("verdict").asText()).as("verdict seq=" + item.path("seq")).isEqualTo(verdict);
        if (canonicalUrl == null) {
            assertThat(item.path("canonicalUrl").isNull()).as("canonicalUrl null").isTrue();
        } else {
            assertThat(item.path("canonicalUrl").asText()).isEqualTo(canonicalUrl);
        }
        if (existingWorkId == null) {
            assertThat(item.path("existingWorkId").isNull()).as("existingWorkId null").isTrue();
        } else {
            assertThat(item.path("existingWorkId").asLong()).isEqualTo(existingWorkId);
        }
        if (duplicateOfSeq == null) {
            assertThat(item.path("duplicateOfSeq").isNull()).as("duplicateOfSeq null").isTrue();
        } else {
            assertThat(item.path("duplicateOfSeq").asInt()).isEqualTo(duplicateOfSeq);
        }
        assertThat(item.path("inputUrl").isTextual()).isTrue();
    }
}
