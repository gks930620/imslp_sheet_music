package com.test.test.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.test.test.integration.support.CrawlTestSupport;
import com.test.test.integration.support.FakeImslpClient;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 재수집(REFRESH)의 의미론 — 02_API_명세서 §6-12(2026-09-07 추가), 01_ERD §7 "수집 upsert".
 *
 * <p>수집은 <b>몇 번이고 다시 도는 작업</b>이다(판정 규칙 개정·IMSLP 갱신·실패 재시도). 그래서 "두 번째 수집이
 * 무엇을 덮고 무엇을 지키는가"가 곧 데이터 신뢰도다. 지금까지 이 계약은 "파일·판정·메모는 보존" 한 줄뿐이었고,
 * 아래 두 가지가 비어 있었다.
 *
 * <ol>
 *   <li><b>사람이 고친 판본 정보</b>(종류·범위·출판사·편집자…)를 재수집이 조용히 되돌린다.
 *       판본 25,000개 규모에서 관리자가 손으로 고친 값이 다음 수집에 사라지면 관리 작업 자체가 무의미해진다.
 *       특히 {@code scope}/{@code kind} 가 되돌아가면 <b>추천 판본이 갑자기 편곡·악장 발췌</b>가 될 수 있다.</li>
 *   <li><b>IMSLP 표기가 재배포 불가로 바뀌었을 때</b>(CC BY-SA → CC BY-NC-ND 등) 자동 판정(§5-11)으로 열어 둔
 *       판본이 계속 열려 있다. 우리 서버가 재배포하면 안 되는 파일을 계속 내려주게 되므로 이건 법적 리스크다.
 *       <b>단 사람이 내린 판정은 뒤집지 않는다</b> — 이 저장소의 일관된 원칙(수집이 관리자 판단을 덮지 않는다)이고,
 *       사람은 표기 외의 근거(작곡가 사후 연수 등)로 판단했을 수 있다.</li>
 * </ol>
 */
class RecrawlContractIntegrationTest extends CrawlTestSupport {

    private static final String MOONLIGHT = FakeImslpClient.MOONLIGHT_URL;
    private static final String BACH = FakeImslpClient.BACH_INVENTIONS_URL;
    private static final String AUTO_ACTOR = "system:auto";
    /** 라이선스가 재배포 불가로 바뀐 같은 페이지 픽스처 (#02001 CC BY-NC, #02002 CC BY-NC-ND). */
    private static final String BACH_DOWNGRADED = "bach_inventions_nc";

    // ===== 1. 관리자 편집 보존 =====

    @Test
    @DisplayName("재수집은 관리자가 고친 판본 정보를 덮지 않는다 — IMSLP 거울 필드만 갱신한다")
    void refresh_keepsAdminEdits() throws Exception {
        Tokens admin = loginAdmin();
        long workId = crawlSingle(admin, MOONLIGHT, false);
        JsonNode before = editionByImslpFileId(getWork(admin, workId), "00014");
        long editionId = before.path("id").asLong();

        // §5-3 — 관리자가 IMSLP 원문이 틀렸다고 판단해 고친다
        Map<String, Object> save = editionSaveBodyFrom(before);
        save.put("publisher", "관리자가 고친 출판사");
        save.put("editor", "관리자 편집자");
        save.put("publishYear", 1888);
        save.put("scope", "MOVEMENT");
        save.put("movementNumber", 2);
        adminPut(admin, "/api/admin/editions/{id}", save, editionId).andExpect(status().isOk());

        // 같은 주소를 refresh 로 다시 수집한다
        long jobId = data(postJob(admin, jobBody(false, List.of(item(MOONLIGHT, true))))
                .andExpect(status().isCreated())).path("id").asLong();
        JsonNode job = awaitJobStatus(admin, jobId, "COMPLETED");
        assertThat(item(job, 1).path("mode").asText()).isEqualTo("REFRESH");

        JsonNode after = getEdition(admin, editionId);
        assertThat(after.path("publisher").asText()).isEqualTo("관리자가 고친 출판사");
        assertThat(after.path("editor").asText()).isEqualTo("관리자 편집자");
        assertThat(after.path("publishYear").asInt()).isEqualTo(1888);
        assertThat(after.path("scope").asText()).isEqualTo("MOVEMENT");
        assertThat(after.path("movementNumber").asInt()).isEqualTo(2);

        // 사람이 만질 수 없는 IMSLP 거울 필드는 계속 갱신된다 (수집을 통째로 무력화하는 게 아니다)
        assertThat(after.path("imslpFileId").asText()).isEqualTo("00014");
        assertThat(after.path("imslpDownloadCount").isNull()).isFalse();
    }

    @Test
    @DisplayName("관리자가 고친 적 없는 판본은 재수집이 그대로 갱신한다")
    void refresh_stillUpdatesUntouchedEditions() throws Exception {
        Tokens admin = loginAdmin();
        long workId = crawlSingle(admin, BACH, false);
        long editionId = editionByImslpFileId(getWork(admin, workId), "02002").path("id").asLong();
        assertThat(getEdition(admin, editionId).path("imslpLicenseCode").asText()).isEqualTo("CC_BY_SA");

        fakeImslp.registerFixture(BACH, BACH_DOWNGRADED);
        long jobId = data(postJob(admin, jobBody(false, List.of(item(BACH, true))))
                .andExpect(status().isCreated())).path("id").asLong();
        awaitJobStatus(admin, jobId, "COMPLETED");

        JsonNode after = getEdition(admin, editionId);
        assertThat(after.path("imslpLicenseCode").asText()).isEqualTo("CC_BY_NC_ND");
        assertThat(after.path("imslpCopyrightText").asText())
                .isEqualTo("Creative Commons Attribution-NonCommercial-NoDerivatives 4.0");
    }

    // ===== 2. 라이선스 강등 회수 =====

    @Test
    @DisplayName("재수집으로 표기가 재배포 불가가 되면 자동 판정으로 열린 판본은 UNKNOWN 으로 닫힌다 (사람 판정은 유지)")
    void refresh_revokesAutoJudgementWhenLicenseIsDowngraded() throws Exception {
        Tokens admin = loginAdmin();
        long workId = crawlSingle(admin, BACH, false);
        JsonNode work = getWork(admin, workId);
        long autoEditionId = editionByImslpFileId(work, "02002").path("id").asLong();   // CC BY-SA → 자동 FREE
        long humanEditionId = editionByImslpFileId(work, "02001").path("id").asLong();  // Public Domain

        // 수집이 만든 작곡가에는 사망 연도가 없다 — 자동 판정의 전제라 관리자가 채운다 (§4-4)
        long composerId = work.path("composer").path("id").asLong();
        adminPut(admin, "/api/admin/composers/{id}", json(
                "nameKo", "바흐",
                "nameOriginal", work.path("composer").path("nameOriginal").asText(),
                "aliases", List.of(),
                "birthYear", 1685,
                "deathYear", 1750,
                "nationality", "독일",
                "imslpUrl", null), composerId).andExpect(status().isOk());

        // 사람이 먼저 판정한 판본은 자동 판정 대상에서 빠진다 (§5-9)
        adminPut(admin, "/api/admin/editions/{id}/copyright",
                json("koreaCopyright", "FREE", "copyrightNote", "사람이 확인한 근거"), humanEditionId)
                .andExpect(status().isOk());

        adminPost(admin, "/api/admin/copyright/auto-judge", json()).andExpect(status().isOk());
        JsonNode autoJudged = getEdition(admin, autoEditionId);
        assertThat(autoJudged.path("koreaCopyright").asText()).isEqualTo("FREE");
        assertThat(autoJudged.path("copyrightJudgedBy").asText()).isEqualTo(AUTO_ACTOR);

        // IMSLP 표기가 재배포 불가로 바뀐 채 재수집된다
        fakeImslp.registerFixture(BACH, BACH_DOWNGRADED);
        long jobId = data(postJob(admin, jobBody(false, List.of(item(BACH, true))))
                .andExpect(status().isCreated())).path("id").asLong();
        awaitJobStatus(admin, jobId, "COMPLETED");

        JsonNode revoked = getEdition(admin, autoEditionId);
        assertThat(revoked.path("imslpLicenseCode").asText()).isEqualTo("CC_BY_NC_ND");
        assertThat(revoked.path("koreaCopyright").asText())
                .as("재배포 불가 표기로 바뀐 자동 판정 판본은 다시 UNKNOWN")
                .isEqualTo("UNKNOWN");
        assertThat(revoked.path("copyrightJudgedBy").isNull()).isTrue();
        assertThat(revoked.path("copyrightNote").isNull()).isTrue();
        assertThat(revoked.path("downloadable").asBoolean()).isFalse();

        JsonNode human = getEdition(admin, humanEditionId);
        assertThat(human.path("imslpLicenseCode").asText()).isEqualTo("CC_BY_NC");
        assertThat(human.path("koreaCopyright").asText())
                .as("사람이 내린 판정은 수집이 뒤집지 않는다")
                .isEqualTo("FREE");
        assertThat(human.path("copyrightJudgedBy").asText()).isEqualTo(ADMIN_USERNAME);
    }
}
