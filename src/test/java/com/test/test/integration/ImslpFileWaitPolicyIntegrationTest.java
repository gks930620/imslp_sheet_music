package com.test.test.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.test.test.integration.support.CrawlTestSupport;
import com.test.test.integration.support.FakeImslpClient;
import com.test.test.integration.support.ImslpCallLog;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.test.test.integration.support.ImslpCallLog.FILE_WAIT;
import static com.test.test.integration.support.ImslpCallLog.downloadEvent;
import static com.test.test.integration.support.ImslpCallLog.resolveEvent;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * IMSLP 파일 대기 정책 (기획 §9-1, 00 §5, 02 §6-10, 03 §3) — TDD Red.
 *
 * <p><b>왜 이 테스트가 따로 있나.</b> 우리는 사용자·IMSLP 양쪽에 "IMSLP 의 15초 대기를 우회하지 않고
 * 그대로 기다린다(파일당 최소 15초 + 요청 간 2초)" 고 약속했다. 이건 응답 JSON 어디에도 드러나지 않아서
 * 기존 수집 테스트를 전부 통과하면서도 정책을 통째로 어길 수 있다 —
 * 2026-09-07 에 두 번 어겼다. 처음엔 {@code awaitFileWait()} 가 <b>어디에서도 호출되지 않았고</b>,
 * 고친 뒤에는 <b>엉뚱한 자리</b>에서 호출됐다(대기 페이지를 열기도 전에 15초를 써버리고,
 * 카운트다운을 띄운 페이지를 받은 뒤에는 0.3초 만에 파일을 쳤다).
 *
 * <p><b>무엇을 계약으로 박는가.</b> 파일 1개마다 이벤트가 정확히 이 순서로 나온다:
 *
 * <pre>
 *   RESOLVE:{id}  (대기 페이지 GET — 여기서 카운트다운이 시작된다)
 *   FILE_WAIT     (카운트다운을 그대로 기다린다)
 *   DOWNLOAD:{id} (파일 호스트 GET)
 * </pre>
 *
 * 브라우저와 같은 순서다. 파일 N개면 이 3연이 N번이다(항목당 1회가 아니다).
 * 메타데이터만 읽을 때는 셋 다 없다. 대기 페이지가 무응답이면 카운트다운을 태우지 않는다.
 * 관찰은 {@link ImslpCallLog} — 게이트와 가짜 클라이언트가 같은 기록장에 순서대로 이벤트를 남긴다.
 * 대기 시간 자체는 테스트 설정이 0ms 라 실제로 자지 않는다("어디서 불렀는가"만 본다).
 *
 * <p>2초 요청 간격({@code awaitRequestSlot})을 몇 번 어디에 두는지는 검증하지 않는다 — 구현 자유다.
 */
@DisplayName("IMSLP 파일 대기 정책 (대기 페이지 → 15초 → 파일)")
class ImslpFileWaitPolicyIntegrationTest extends CrawlTestSupport {

    private static final String MOONLIGHT = FakeImslpClient.MOONLIGHT_URL;
    private static final String ELISE = FakeImslpClient.ELISE_URL;

    @Test
    @DisplayName("수집 잡: 파일마다 '대기 페이지 → 15초 → 파일' 순서 — 곡 1개·파일 2개면 그 3연이 2번")
    void crawl_job_waits_the_countdown_between_wait_page_and_file() throws Exception {
        Tokens admin = loginAdmin();

        long jobId = startJob(admin, true, MOONLIGHT).path("id").asLong();
        JsonNode done = awaitJobStatus(admin, jobId, "COMPLETED");

        JsonNode item = item(done, 1);
        assertThat(item.path("status").asText()).isEqualTo("SUCCESS");
        assertThat(item.path("fileCount").asInt()).isEqualTo(2);
        assertThat(fakeImslp.getDownloadedFileIds()).containsExactly("32718", "00014");

        // 핵심: 대기는 대기 페이지를 받은 "뒤", 파일을 치기 "앞" 이다.
        assertThat(imslpCalls.fileEvents())
                .as("브라우저와 같은 순서여야 한다 — 대기 페이지 → 카운트다운 → 파일 (기획 §9-1)")
                .containsExactlyElementsOf(ImslpCallLog.expectedFileEvents("32718", "00014"));
        assertThat(imslpCalls.fileWaitCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("수집 잡: 대기는 항목당 1회가 아니라 파일당 1회 — 곡 2개·파일 4개면 4회")
    void crawl_job_waits_once_per_file_not_once_per_item() throws Exception {
        Tokens admin = loginAdmin();

        long jobId = startJob(admin, true, MOONLIGHT, ELISE).path("id").asLong();
        awaitJobStatus(admin, jobId, "COMPLETED");

        assertThat(fakeImslp.getDownloadedFileIds()).containsExactly("32718", "00014", "103834", "05929");
        assertThat(imslpCalls.fileEvents())
                .containsExactlyElementsOf(ImslpCallLog.expectedFileEvents("32718", "00014", "103834", "05929"));
        assertThat(imslpCalls.fileWaitCount()).isEqualTo(4);
    }

    @Test
    @DisplayName("메타데이터만 읽는 수집은 대기 페이지도 카운트다운도 없다 (요청 간격 게이트만 쓴다)")
    void metadata_only_crawl_does_not_wait_the_file_countdown() throws Exception {
        Tokens admin = loginAdmin();

        long jobId = startJob(admin, false, MOONLIGHT).path("id").asLong();
        awaitJobStatus(admin, jobId, "COMPLETED");

        assertThat(fakeImslp.getDownloadedFileIds()).isEmpty();
        assertThat(imslpCalls.fileEvents())
                .as("15초 대기는 파일 대기 페이지의 것이다 — 메타 읽기에 붙이면 수집이 무의미하게 느려진다")
                .isEmpty();
        assertThat(imslpCalls.requestSlotCount())
                .as("작품 페이지 1 + 위키텍스트 1 은 2초 간격 게이트를 지난다")
                .isGreaterThanOrEqualTo(2);
    }

    @Test
    @DisplayName("판본 개별 받아오기(§5-7)도 대기 페이지 → 15초 → 파일 순서를 지킨다")
    void single_edition_fetch_waits_the_countdown_between_wait_page_and_file() throws Exception {
        Tokens admin = loginAdmin();
        long workId = crawlSingle(admin, MOONLIGHT, false);
        long editionId = editionByImslpFileId(getWork(admin, workId), "15808").path("id").asLong();
        assertThat(imslpCalls.fileWaitCount()).isZero();     // 메타 수집 단계에서는 아직 0

        adminPost(admin, "/api/admin/editions/{id}/fetch-file", json(), editionId)
                .andExpect(status().isAccepted());
        awaitEdition(admin, editionId, e -> e.path("hasFile").asBoolean(), "hasFile=true");

        assertThat(imslpCalls.fileEvents())
                .containsExactlyElementsOf(ImslpCallLog.expectedFileEvents("15808"));
    }

    /**
     * 02 §6-10 보강(2026-09-07) — 파일 수신 중 IMSLP 무응답은 사유를 {@code IMSLP_UNAVAILABLE} 로 두되
     * (302·429·5xx 는 "물러서라"는 신호라 연속 3회 PAUSED 백오프를 타야 한다),
     * 그때까지 저장된 {@code editionCount} 와 받은 {@code fileCount} 는 항목에 남겨야 한다.
     */
    @Test
    @DisplayName("파일 호스트 무응답: 사유는 IMSLP_UNAVAILABLE, 단 저장된 판본 수·받은 파일 수는 남는다")
    void file_stage_unavailable_keeps_saved_counts() throws Exception {
        Tokens admin = loginAdmin();
        fakeImslp.markFileUnavailable("00014");          // 두 번째 파일의 파일 호스트만 튕긴 상황

        long jobId = startJob(admin, true, MOONLIGHT).path("id").asLong();
        JsonNode done = awaitJobStatus(admin, jobId, "COMPLETED");

        JsonNode item = item(done, 1);
        assertThat(item.path("status").asText()).isEqualTo("FAILED");
        assertThat(item.path("failReason").asText()).isEqualTo("IMSLP_UNAVAILABLE");
        assertThat(item.path("message").asText()).isEqualTo("IMSLP가 응답하지 않아요");
        assertThat(item.path("editionCount").asInt()).isEqualTo(8);
        assertThat(item.path("fileCount").asInt()).isEqualTo(1);

        JsonNode work = getWork(admin, item.path("workId").asLong());
        assertThat(editionByImslpFileId(work, "32718").path("hasFile").asBoolean()).isTrue();
        assertThat(editionByImslpFileId(work, "00014").path("hasFile").asBoolean()).isFalse();

        // 실패한 파일도 카운트다운은 지키고 나서 쳤다 — 대기 없이 파일을 친 흔적이 있으면 안 된다.
        assertThat(imslpCalls.fileEvents())
                .containsExactlyElementsOf(ImslpCallLog.expectedFileEvents("32718", "00014"));
    }

    /**
     * 대기 자리가 대기 페이지 <b>앞</b>이면 통과할 수 없는 케이스 — 순서를 못으로 박는 테스트.
     *
     * <p>대기 페이지가 봇 게이트로 튕기면 받을 파일 주소 자체가 없다. 그런데도 15초를 잤다면
     * 대기가 대기 페이지보다 앞에 있다는 뜻이다. 실무에서도 이 15초는 순수한 낭비고(파일은 못 받는데 워커만 멈춘다),
     * 무응답이 연속될 때 PAUSED 로 물러서는 속도를 늦춘다.
     */
    @Test
    @DisplayName("대기 페이지가 무응답이면 카운트다운을 태우지 않고 파일도 치지 않는다")
    void wait_page_failure_does_not_burn_the_countdown() throws Exception {
        Tokens admin = loginAdmin();
        fakeImslp.markFileWaitPageUnavailable("00014");   // 두 번째 파일의 대기 페이지가 봇 게이트에 튕긴 상황

        long jobId = startJob(admin, true, MOONLIGHT).path("id").asLong();
        JsonNode done = awaitJobStatus(admin, jobId, "COMPLETED");

        JsonNode item = item(done, 1);
        assertThat(item.path("status").asText()).isEqualTo("FAILED");
        assertThat(item.path("failReason").asText()).isEqualTo("IMSLP_UNAVAILABLE");
        assertThat(item.path("fileCount").asInt()).isEqualTo(1);

        assertThat(fakeImslp.getDownloadedFileIds())
                .as("주소를 못 얻었으면 파일 호스트를 치지 않는다")
                .containsExactly("32718");
        assertThat(imslpCalls.fileEvents())
                .as("두 번째 파일은 대기 페이지에서 끝난다 — 그 뒤에 FILE_WAIT 가 붙으면 대기가 잘못된 자리에 있다는 뜻")
                .containsExactly(resolveEvent("32718"), FILE_WAIT, downloadEvent("32718"), resolveEvent("00014"));
        assertThat(imslpCalls.fileWaitCount())
                .as("받을 수 없는 파일 때문에 15초를 자면 안 된다")
                .isEqualTo(1);
    }
}
