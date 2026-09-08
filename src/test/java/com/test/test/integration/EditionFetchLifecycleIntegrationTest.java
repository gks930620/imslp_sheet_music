package com.test.test.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.test.test.integration.support.CrawlTestSupport;
import com.test.test.integration.support.FakeImslpClient;
import com.test.test.sheetmusic.crawl.CrawlStartupRecovery;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 판본 파일 받아오기의 수명주기 — 02_API_명세서 §5-7·§6-10 (2026-09-07 보완).
 *
 * <p>받아오기는 <b>비동기</b>다(요청 202 → 워커 스레드가 나중에 붙인다). 그 사이에 서버가 재시작하거나
 * 관리자가 같은 판본을 만지면 무슨 일이 벌어지는지가 계약에 없었다. 두 구멍을 테스트로 박는다.
 *
 * <ol>
 *   <li><b>재시작.</b> {@code file_fetch_status} 가 {@code QUEUED/FETCHING} 인 채 프로세스가 죽으면 그 작업은
 *       영원히 사라지는데 상태 컬럼은 그대로 남는다. §5-7 은 그 상태에서 409("이미 받아오는 중이에요")를 내므로
 *       그 판본은 <b>DB 를 손으로 고치기 전까지 영구히 받아올 수 없다</b>.
 *       {@code CrawlStartupRecovery} 가 처리 중이던 수집 항목을 INTERRUPTED 로 되돌리듯, 이 상태도 되돌려야 한다.</li>
 *   <li><b>겹침.</b> 받아오는 동안 관리자가 §5-3 으로 그 판본에 직접 PDF 를 올려 붙이면, 뒤늦게 도착한 수집 파일이
 *       관리자 파일을 조용히 밀어낸다. 밀려난 {@code files} 행은 {@code ref_id = 판본 id} 라
 *       orphan 배치(§5-3-1 ③ — {@code ref_id = 0} 만 본다)도 지우지 못해 <b>바이트가 영구히 남는다</b>.
 *       계약: <b>이미 파일이 있는 판본에는 붙이지 않고, 받아온 파일 행을 지운다.</b>
 *       (수집이 관리자 입력을 덮지 않는다는 이 저장소의 원칙 — 02 §6-10·§6-11 과 같은 방향)</li>
 * </ol>
 */
class EditionFetchLifecycleIntegrationTest extends CrawlTestSupport {

    private static final String BACH = FakeImslpClient.BACH_INVENTIONS_URL;
    private static final String FETCH_URL = "/api/admin/editions/{id}/fetch-file";

    @Autowired
    private CrawlStartupRecovery crawlStartupRecovery;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("재시작 복구는 '받아오는 중'에 갇힌 판본을 FAILED 로 풀어 다시 요청할 수 있게 한다")
    void restartRecovery_releasesStuckFetch() throws Exception {
        Tokens admin = loginAdmin();
        long workId = crawlSingle(admin, BACH, false);
        long editionId = editionByImslpFileId(getWork(admin, workId), "02001").path("id").asLong();

        fakeImslp.holdFiles();
        adminPost(admin, FETCH_URL, json(), editionId).andExpect(status().isAccepted());
        awaitEdition(admin, editionId,
                e -> "FETCHING".equals(e.path("fileFetchStatus").asText()), "fileFetchStatus=FETCHING");

        // 서비스 재시작 = 기동 복구 러너 재실행 (02 §6-10)
        crawlStartupRecovery.run(new DefaultApplicationArguments());

        JsonNode after = getEdition(admin, editionId);
        assertThat(after.path("fileFetchStatus").asText())
                .as("재시작으로 사라진 비동기 작업의 흔적은 FAILED 로 정리된다")
                .isEqualTo("FAILED");
        assertThat(after.path("fileFetchError").asText()).isNotBlank();

        // 갇히지 않았으므로 다시 요청할 수 있다 (409 가 아니다)
        adminPost(admin, FETCH_URL, json(), editionId).andExpect(status().isAccepted());
    }

    @Test
    @DisplayName("받아오는 사이 관리자가 올린 파일이 있으면 수집 파일은 붙지 않고 버려진다 (files 행도 남기지 않는다)")
    void fetchedFile_doesNotOverwriteAdminUpload() throws Exception {
        Tokens admin = loginAdmin();
        long workId = crawlSingle(admin, BACH, false);
        long editionId = editionByImslpFileId(getWork(admin, workId), "02001").path("id").asLong();

        fakeImslp.holdFiles();
        adminPost(admin, FETCH_URL, json(), editionId).andExpect(status().isAccepted());
        awaitEdition(admin, editionId,
                e -> "FETCHING".equals(e.path("fileFetchStatus").asText()), "fileFetchStatus=FETCHING");

        // 받아오는 동안 관리자가 직접 PDF 를 올려 붙인다 (§5-1 → §5-3)
        JsonNode upload = uploadSamplePdf(admin);
        long adminPdfFileId = upload.path("fileId").asLong();
        Map<String, Object> save = editionSaveBodyFrom(getEdition(admin, editionId));
        save.put("fileId", adminPdfFileId);
        save.put("previewFileId", upload.path("previewFileId").asLong());
        adminPut(admin, "/api/admin/editions/{id}", save, editionId).andExpect(status().isOk());

        fakeImslp.releaseFiles();
        awaitEdition(admin, editionId, e -> e.path("fileFetchStatus").isNull(), "받아오기 종료");

        JsonNode after = getEdition(admin, editionId);
        assertThat(after.path("pdfFileId").asLong())
                .as("관리자가 올린 파일이 그대로 남는다")
                .isEqualTo(adminPdfFileId);
        assertThat(editionFileRows())
                .as("받아온 PDF·미리보기 행이 남으면 ref_id≠0 이라 orphan 배치도 못 지운다")
                .isEqualTo(2);
    }

    /** 판본에 연결된 files 행 수 — HTTP 로는 드러나지 않는 부수효과라 DB 로 직접 본다(§5-3-1 ②). */
    private int editionFileRows() {
        Integer count = jdbc.queryForObject(
                "select count(*) from files where ref_type = 'EDITION'", Integer.class);
        return count == null ? 0 : count;
    }
}
