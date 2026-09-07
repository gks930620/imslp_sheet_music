package com.test.test.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.test.test.integration.support.CrawlTestSupport;
import com.test.test.integration.support.FakeImslpClient;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 판본 "파일 받아오기" 비동기 흐름 (02_API_명세서 §5-7, 기획 §9-1 라이선스 조건) — TDD Red.
 * imslpFileId 가 있는 판본은 수집으로만 생기므로 {@link FakeImslpClient} 로 월광·엘리제를 메타만(fetchFiles=false) 먼저 수집한다.
 * 동기 400 검증(파일 있음·IMSLP 정보 없음)은 {@link AdminEditionApiIntegrationTest}.
 */
class EditionFetchFileApiIntegrationTest extends CrawlTestSupport {

    @Test
    void fetch_file_returns_202_then_edition_gets_file_preview_and_page_count() throws Exception {
        Tokens admin = loginAdmin();
        long workId = crawlSingle(admin, FakeImslpClient.MOONLIGHT_URL, false);
        long editionId = editionByImslpFileId(getWork(admin, workId), "15808").path("id").asLong();

        adminPost(admin, "/api/admin/editions/{id}/fetch-file", json(), editionId)
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.editionId").value(editionId))
                .andExpect(jsonPath("$.data.fileFetchStatus").value("QUEUED"));

        JsonNode fetched = awaitEdition(admin, editionId, e -> e.path("hasFile").asBoolean(), "hasFile=true");
        assertThat(fetched.path("fileFetchStatus").isNull()).isTrue();
        assertThat(fetched.path("fileFetchError").isNull()).isTrue();
        assertThat(fetched.path("fileFetchedAt").isTextual()).isTrue();
        assertThat(fetched.path("fileSize").asLong()).isEqualTo(samplePdfBytes().length);
        assertThat(fetched.path("pageCount").asInt()).isEqualTo(FakeImslpClient.SAMPLE_PDF_PAGE_COUNT);
        assertThat(fetched.path("previewUrl").asText()).startsWith("/uploads/").endsWith(".png");
        assertThat(fetched.path("pdfFileId").asLong()).isPositive();
        assertThat(fetched.path("previewFileId").asLong()).isPositive();
        assertThat(fetched.path("isCandidate").asBoolean()).isTrue();   // 곡에 파일 있는 전곡 판본이 이것뿐
        assertThat(fetched.path("workStatus").asText()).isEqualTo("PREPARING");
        assertThat(fakeImslp.getDownloadedFileIds()).containsExactly("15808");

        // 파일이 생겼으니 두 번째 요청은 400
        adminPost(admin, "/api/admin/editions/{id}/fetch-file", json(), editionId)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("이미 파일이 있는 판본이에요"));
    }

    @Test
    void fetch_file_conflicts_while_queued_or_fetching() throws Exception {
        Tokens admin = loginAdmin();
        long workId = crawlSingle(admin, FakeImslpClient.MOONLIGHT_URL, false);
        long editionId = editionByImslpFileId(getWork(admin, workId), "926972").path("id").asLong();

        fakeImslp.holdFiles();
        adminPost(admin, "/api/admin/editions/{id}/fetch-file", json(), editionId).andExpect(status().isAccepted());
        adminPost(admin, "/api/admin/editions/{id}/fetch-file", json(), editionId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("DUPLICATE_RESOURCE"));

        JsonNode polling = getEdition(admin, editionId);
        assertThat(polling.path("fileFetchStatus").asText()).isIn("QUEUED", "FETCHING");
        assertThat(polling.path("hasFile").asBoolean()).isFalse();

        fakeImslp.releaseFiles();
        awaitEdition(admin, editionId, e -> e.path("hasFile").asBoolean(), "hasFile=true");
    }

    @Test
    void fetch_file_is_refused_for_non_redistributable_license() throws Exception {
        Tokens admin = loginAdmin();
        long workId = crawlSingle(admin, FakeImslpClient.ELISE_URL, false);
        long ncEdition = editionByImslpFileId(getWork(admin, workId), "103779").path("id").asLong();   // CC BY-NC-SA 3.0

        adminPost(admin, "/api/admin/editions/{id}/fetch-file", json(), ncEdition)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_RULE_VIOLATION"))
                .andExpect(jsonPath("$.message").value("재배포가 허용되지 않는 표기라 받아올 수 없어요"));
        assertThat(fakeImslp.getDownloadedFileIds()).isEmpty();
    }

    @Test
    void failed_fetch_records_error_and_can_be_retried() throws Exception {
        Tokens admin = loginAdmin();
        long workId = crawlSingle(admin, FakeImslpClient.MOONLIGHT_URL, false);
        long editionId = editionByImslpFileId(getWork(admin, workId), "00014").path("id").asLong();

        fakeImslp.markFileCorrupt("00014");
        adminPost(admin, "/api/admin/editions/{id}/fetch-file", json(), editionId).andExpect(status().isAccepted());
        JsonNode failed = awaitEdition(admin, editionId, e -> "FAILED".equals(e.path("fileFetchStatus").asText()), "fileFetchStatus=FAILED");
        assertThat(failed.path("hasFile").asBoolean()).isFalse();
        assertThat(failed.path("fileFetchError").asText()).isNotBlank();

        // FAILED 상태에서는 다시 요청할 수 있다
        fakeImslp.reset();
        adminPost(admin, "/api/admin/editions/{id}/fetch-file", json(), editionId).andExpect(status().isAccepted());
        JsonNode fetched = awaitEdition(admin, editionId, e -> e.path("hasFile").asBoolean(), "hasFile=true");
        assertThat(fetched.path("fileFetchStatus").isNull()).isTrue();
        assertThat(fetched.path("fileFetchError").isNull()).isTrue();
    }
}
