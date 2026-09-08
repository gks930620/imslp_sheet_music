package com.test.test.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.test.test.integration.support.CrawlTestSupport;
import com.test.test.integration.support.FakeImslpClient;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 건반 독주 판정의 수집 경로 (02_API_명세서 §6-11) — TDD Red.
 *
 * <p>판정 함수 자체는 {@code KeyboardSoloRuleTest} 가 표로 덮는다. 여기서는 <b>수집이 그 판정을 어떻게 쓰는지</b>만 본다:
 * 하프시코드 곡이 숨겨지지 않는 것, 관현악곡은 여전히 숨겨지는 것, 재수집이 "수집이 숨긴 것만" 푸는 것.
 */
class CrawlKeyboardSoloIntegrationTest extends CrawlTestSupport {

    @Test
    void harpsichord_work_is_collected_and_not_hidden() throws Exception {
        Tokens admin = loginAdmin();

        long jobId = startJob(admin, false, FakeImslpClient.BACH_INVENTIONS_URL).path("id").asLong();
        JsonNode job = awaitJobStatus(admin, jobId, "COMPLETED");

        JsonNode item = item(job, 1);
        assertThat(item.path("status").asText()).isEqualTo("SUCCESS");
        assertThat(item.path("editionCount").asInt()).isEqualTo(2);
        assertThat(job.path("hiddenCount").asInt()).isZero();
        assertThat(job.path("successCount").asInt()).isEqualTo(1);

        JsonNode work = getWork(admin, item.path("workId").asLong());
        assertThat(work.path("hidden").asBoolean()).isFalse();
        assertThat(work.path("hiddenReason").isNull()).isTrue();
        assertThat(work.path("titleOriginal").asText()).isEqualTo("15 Inventions, BWV 772-786");

        // 라이선스 코드가 판본에 채워진다 — 자동 판정(§5-11)이 이 값을 본다
        assertThat(editionByImslpFileId(work, "02001").path("imslpLicenseCode").asText()).isEqualTo("PD");
        assertThat(editionByImslpFileId(work, "02002").path("imslpLicenseCode").asText()).isEqualTo("CC_BY_SA");
    }

    @Test
    void non_keyboard_work_is_still_hidden() throws Exception {
        Tokens admin = loginAdmin();

        long jobId = startJob(admin, false, FakeImslpClient.SYMPHONY5_URL).path("id").asLong();
        JsonNode job = awaitJobStatus(admin, jobId, "COMPLETED");

        JsonNode item = item(job, 1);
        assertThat(item.path("status").asText()).isEqualTo("HIDDEN");
        assertThat(item.path("message").asText()).isEqualTo("피아노 독주곡이 아닌 것 같아요");
        JsonNode work = getWork(admin, item.path("workId").asLong());
        assertThat(work.path("hidden").asBoolean()).isTrue();
        assertThat(work.path("hiddenReason").asText()).isEqualTo("NOT_PIANO_SOLO");
    }

    @Test
    void refreshing_unhides_a_work_that_the_crawler_had_hidden() throws Exception {
        Tokens admin = loginAdmin();

        // 1) 규칙 개정 전처럼 "건반 독주가 아니다"로 읽히는 페이지를 수집 → 숨김
        fakeImslp.registerFixture(FakeImslpClient.BACH_INVENTIONS_URL, "symphony5");
        long firstJob = startJob(admin, false, FakeImslpClient.BACH_INVENTIONS_URL).path("id").asLong();
        JsonNode hiddenItem = item(awaitJobStatus(admin, firstJob, "COMPLETED"), 1);
        assertThat(hiddenItem.path("status").asText()).isEqualTo("HIDDEN");
        long workId = hiddenItem.path("workId").asLong();
        assertThat(getWork(admin, workId).path("hiddenReason").asText()).isEqualTo("NOT_PIANO_SOLO");

        // 2) 같은 주소가 이제 건반 독주로 읽힌다(= 판정 규칙 개정) → refresh 로 다시 수집
        fakeImslp.registerFixture(FakeImslpClient.BACH_INVENTIONS_URL, "bach_inventions");
        long refreshJob = data(postJob(admin, jobBody(false,
                List.of(item(FakeImslpClient.BACH_INVENTIONS_URL, true))))
                .andExpect(status().isCreated())).path("id").asLong();
        JsonNode refreshed = item(awaitJobStatus(admin, refreshJob, "COMPLETED"), 1);
        // 판본이 0개인 곡이라 판정은 ATTACH 다(§6-1 규칙 5). 재수집이면 모드와 무관하게 숨김을 다시 계산한다.
        assertThat(refreshed.path("mode").asText()).isIn("REFRESH", "ATTACH");
        assertThat(refreshed.path("status").asText()).isEqualTo("SUCCESS");

        JsonNode work = getWork(admin, workId);
        assertThat(work.path("hidden").asBoolean()).isFalse();
        assertThat(work.path("hiddenReason").isNull()).isTrue();
    }

    @Test
    void refreshing_does_not_unhide_a_work_the_admin_hid_on_purpose() throws Exception {
        Tokens admin = loginAdmin();

        long workId = crawlSingle(admin, FakeImslpClient.BACH_INVENTIONS_URL, false);
        JsonNode work = getWork(admin, workId);
        assertThat(work.path("hidden").asBoolean()).isFalse();

        // 관리자가 직접 숨긴다 (hiddenReason 은 null — 01_ERD §3-3)
        adminPut(admin, "/api/admin/works/{id}", adminHideBody(work), workId).andExpect(status().isOk());
        assertThat(getWork(admin, workId).path("hidden").asBoolean()).isTrue();
        assertThat(getWork(admin, workId).path("hiddenReason").isNull()).isTrue();

        long refreshJob = data(postJob(admin, jobBody(false,
                List.of(item(FakeImslpClient.BACH_INVENTIONS_URL, true))))
                .andExpect(status().isCreated())).path("id").asLong();
        assertThat(item(awaitJobStatus(admin, refreshJob, "COMPLETED"), 1).path("status").asText())
                .isEqualTo("SUCCESS");

        JsonNode after = getWork(admin, workId);
        assertThat(after.path("hidden").asBoolean()).isTrue();
        assertThat(after.path("hiddenReason").isNull()).isTrue();
    }

    /** §4-8 곡 수정 요청 — 현재 값을 그대로 두고 hidden 만 true 로. */
    private java.util.Map<String, Object> adminHideBody(JsonNode work) {
        return json(
                "composerId", work.path("composer").path("id").asLong(),
                "titleKo", work.path("titleKo").isNull() ? null : work.path("titleKo").asText(),
                "titleOriginal", work.path("titleOriginal").asText(),
                "catalogNumbers", strings(work.path("catalogNumbers")),
                "aliases", strings(work.path("aliases")),
                "level", work.path("level").isNull() ? null : work.path("level").asText(),
                "compositionYear", work.path("compositionYear").isNull() ? null : work.path("compositionYear").asText(),
                "musicalKey", work.path("musicalKey").isNull() ? null : work.path("musicalKey").asText(),
                "movements", work.path("movements").isNull() ? null : work.path("movements").asText(),
                "movementPageGuide", null,
                "imslpUrl", work.path("imslpUrl").asText(),
                "hidden", true);
    }
}
