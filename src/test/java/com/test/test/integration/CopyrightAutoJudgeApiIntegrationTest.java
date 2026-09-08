package com.test.test.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.test.test.integration.support.AdminApiTestSupport;
import java.time.Year;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 저작권 자동 판정 (02_API_명세서 §5-11·§5-12, 기획 02 부록 A) — TDD Red.
 *
 * <p>시드 곡에는 판본이 없으므로(01_ERD §6) 이 테스트가 만든 판본만 자동 판정 대상이 된다.
 * 테스트 트랜잭션은 롤백되므로 테스트끼리 숫자가 섞이지 않는다.
 *
 * <p>기준 연도(작곡가 사후 70년 / 출판 후 70년·120년)는 "올해"에서 계산되므로, 픽스처의 연도도
 * {@link Year#now()} 에서 상대값으로 만든다. 경계에서 최소 5년 이상 떨어뜨려 해가 바뀌어도 결과가 같다.
 */
class CopyrightAutoJudgeApiIntegrationTest extends AdminApiTestSupport {

    private static final String AUTO_ACTOR = "system:auto";
    private static final String AUTO_JUDGE_URL = "/api/admin/copyright/auto-judge";
    private static final String UNDO_URL = "/api/admin/copyright/auto-judge/undo";

    private static final int THIS_YEAR = Year.now().getValue();
    /** 사후 70년이 한참 지난 작곡가 (2026 기준 1826). */
    private static final int LONG_DEAD = THIS_YEAR - 200;
    /** 사후 70년이 아직 안 지난 작곡가. */
    private static final int RECENTLY_DEAD = THIS_YEAR - 10;
    /** 출판 후 120년 경과 (편집자 표기가 있어도 통과). */
    private static final int OLD_PUBLICATION = THIS_YEAR - 150;
    /** 출판 후 70년은 지났지만 120년은 안 지남 (편집자 표기가 있으면 막힌다). */
    private static final int MIDDLE_PUBLICATION = THIS_YEAR - 90;
    /** 출판 후 70년 미경과. */
    private static final int RECENT_PUBLICATION = THIS_YEAR - 30;

    // ===== §5-11 정상 =====

    @Test
    void auto_judge_opens_only_the_editions_the_rules_allow_and_reports_every_rule_and_skip_reason() throws Exception {
        Tokens admin = loginAdmin();
        Fixture f = createFixture(admin);

        JsonNode result = data(adminPost(admin, AUTO_JUDGE_URL, json())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true)));

        assertThat(result.path("dryRun").asBoolean()).isFalse();
        assertThat(result.path("targetCount").asInt()).isEqualTo(10);
        assertThat(result.path("judgedFree").asInt()).isEqualTo(4);
        assertThat(result.path("remainingUnknown").asInt()).isEqualTo(6);
        assertThat(result.path("recommendedAssigned").asInt()).isEqualTo(1);

        // byRule 은 3개 고정 순서, count 0 인 항목도 빠지지 않는다
        assertThat(names(result.path("byRule"), "rule"))
                .containsExactly("CC_REDISTRIBUTABLE", "PD_NO_EDITOR", "PD_OLD_PUBLICATION");
        assertThat(counts(result.path("byRule"), "rule"))
                .containsEntry("CC_REDISTRIBUTABLE", 1)
                .containsEntry("PD_NO_EDITOR", 2)          // 파일 있는 전곡 + 파일 있는 악장 발췌
                .containsEntry("PD_OLD_PUBLICATION", 1);

        // skipped 는 5개 고정 순서 (부록 A §A-1 표 순서)
        assertThat(names(result.path("skipped"), "reason"))
                .containsExactly("LICENSE_NOT_REDISTRIBUTABLE", "COMPOSER_DEATH_YEAR_UNKNOWN",
                        "COMPOSER_COPYRIGHT_ACTIVE", "PUBLICATION_TOO_RECENT", "EDITOR_UNVERIFIABLE");
        assertThat(counts(result.path("skipped"), "reason"))
                .containsEntry("LICENSE_NOT_REDISTRIBUTABLE", 2)   // CC BY-NC-ND + 표기 없음
                .containsEntry("COMPOSER_DEATH_YEAR_UNKNOWN", 1)
                .containsEntry("COMPOSER_COPYRIGHT_ACTIVE", 1)
                .containsEntry("PUBLICATION_TOO_RECENT", 1)
                .containsEntry("EDITOR_UNVERIFIABLE", 1);

        // 합계 항등식 (02 §5-11)
        assertThat(sum(result.path("byRule"))).isEqualTo(result.path("judgedFree").asInt());
        assertThat(sum(result.path("skipped"))).isEqualTo(result.path("remainingUnknown").asInt());

        // ===== 판본별 결과와 근거 문구 =====
        JsonNode pdNoEditor = getEdition(admin, f.pdNoEditor);
        assertThat(pdNoEditor.path("koreaCopyright").asText()).isEqualTo("FREE");
        assertThat(pdNoEditor.path("copyrightJudgedBy").asText()).isEqualTo(AUTO_ACTOR);
        assertThat(pdNoEditor.path("copyrightJudgedAt").isTextual()).isTrue();
        assertThat(pdNoEditor.path("copyrightNote").asText())
                .isEqualTo("자동 판정: Public Domain + 작곡가 " + LONG_DEAD + "년 사망 + 편집자 표기 없음");
        assertThat(pdNoEditor.path("downloadable").asBoolean()).isTrue();

        assertThat(getEdition(admin, f.ccByShareAlike).path("copyrightNote").asText())
                .isEqualTo("자동 판정: 재배포 허용 라이선스(CC_BY_SA) + 작곡가 " + LONG_DEAD + "년 사망");

        assertThat(getEdition(admin, f.pdOldPublication).path("copyrightNote").asText())
                .isEqualTo("자동 판정: Public Domain + 작곡가 " + LONG_DEAD + "년 사망 + "
                        + OLD_PUBLICATION + "년 출판(120년 경과)");

        // 열리지 않은 것들은 UNKNOWN 그대로, 메모·판정자도 비어 있다
        for (long editionId : new long[]{f.editorUnverifiable, f.publicationTooRecent, f.nonRedistributableLicense,
                f.noLicenseText, f.composerDeathYearUnknown, f.composerCopyrightActive}) {
            JsonNode edition = getEdition(admin, editionId);
            assertThat(edition.path("koreaCopyright").asText()).isEqualTo("UNKNOWN");
            assertThat(edition.path("copyrightNote").isNull()).isTrue();
            assertThat(edition.path("copyrightJudgedBy").isNull()).isTrue();
            assertThat(edition.path("downloadable").asBoolean()).isFalse();
        }

        // ===== 추천 자동 지정 =====
        JsonNode workA = getWork(admin, f.workA);
        assertThat(workA.path("recommendedEditionId").asLong()).isEqualTo(f.pdNoEditor);
        assertThat(workA.path("status").asText()).isEqualTo("READY");

        // 악장 발췌(scope=MOVEMENT)만 가진 곡은 FREE 여도 추천 대상이 아니다 → 계속 PREPARING
        JsonNode workD = getWork(admin, f.workMovementOnly);
        assertThat(workD.path("recommendedEditionId").isNull()).isTrue();
        assertThat(workD.path("status").asText()).isEqualTo("PREPARING");
    }

    @Test
    void auto_judge_dry_run_reports_the_same_numbers_but_changes_nothing() throws Exception {
        Tokens admin = loginAdmin();
        Fixture f = createFixture(admin);

        JsonNode dry = data(adminPost(admin, AUTO_JUDGE_URL, json("dryRun", true, "assignRecommended", true))
                .andExpect(status().isOk()));
        assertThat(dry.path("dryRun").asBoolean()).isTrue();
        assertThat(dry.path("targetCount").asInt()).isEqualTo(10);
        assertThat(dry.path("judgedFree").asInt()).isEqualTo(4);
        assertThat(dry.path("remainingUnknown").asInt()).isEqualTo(6);
        assertThat(dry.path("recommendedAssigned").asInt()).isEqualTo(1);

        // 아무것도 저장하지 않았다
        assertThat(getEdition(admin, f.pdNoEditor).path("koreaCopyright").asText()).isEqualTo("UNKNOWN");
        assertThat(getEdition(admin, f.pdNoEditor).path("copyrightNote").isNull()).isTrue();
        assertThat(getWork(admin, f.workA).path("recommendedEditionId").isNull()).isTrue();

        // 실제 실행은 같은 숫자를 낸다
        JsonNode real = data(adminPost(admin, AUTO_JUDGE_URL, json()).andExpect(status().isOk()));
        assertThat(real.path("targetCount").asInt()).isEqualTo(dry.path("targetCount").asInt());
        assertThat(real.path("judgedFree").asInt()).isEqualTo(dry.path("judgedFree").asInt());
        assertThat(real.path("recommendedAssigned").asInt()).isEqualTo(dry.path("recommendedAssigned").asInt());
    }

    @Test
    void auto_judge_never_overwrites_a_human_judgement_and_is_idempotent() throws Exception {
        Tokens admin = loginAdmin();
        Fixture f = createFixture(admin);

        // 사람이 먼저 판정한 판본은 대상(UNKNOWN)이 아니다
        adminPut(admin, "/api/admin/editions/{id}/copyright",
                json("koreaCopyright", "RESTRICTED", "copyrightNote", "편집자 생몰 확인 필요"), f.editorUnverifiable)
                .andExpect(status().isOk());

        JsonNode first = data(adminPost(admin, AUTO_JUDGE_URL, json()).andExpect(status().isOk()));
        assertThat(first.path("targetCount").asInt()).isEqualTo(9);
        assertThat(first.path("judgedFree").asInt()).isEqualTo(4);
        assertThat(first.path("remainingUnknown").asInt()).isEqualTo(5);
        assertThat(counts(first.path("skipped"), "reason")).containsEntry("EDITOR_UNVERIFIABLE", 0);

        JsonNode human = getEdition(admin, f.editorUnverifiable);
        assertThat(human.path("koreaCopyright").asText()).isEqualTo("RESTRICTED");
        assertThat(human.path("copyrightNote").asText()).isEqualTo("편집자 생몰 확인 필요");
        assertThat(human.path("copyrightJudgedBy").asText()).isEqualTo(ADMIN_USERNAME);

        // 두 번째 실행: 이미 연 것은 다시 세지 않는다 (멱등)
        JsonNode second = data(adminPost(admin, AUTO_JUDGE_URL, json()).andExpect(status().isOk()));
        assertThat(second.path("targetCount").asInt()).isEqualTo(5);
        assertThat(second.path("judgedFree").asInt()).isZero();
        assertThat(second.path("remainingUnknown").asInt()).isEqualTo(5);
        assertThat(second.path("recommendedAssigned").asInt()).isZero();
        assertThat(sum(second.path("byRule"))).isZero();

        // 이미 지정된 추천도 덮어쓰지 않는다
        assertThat(getWork(admin, f.workA).path("recommendedEditionId").asLong()).isEqualTo(f.pdNoEditor);
    }

    @Test
    void auto_judge_without_assign_recommended_leaves_works_preparing() throws Exception {
        Tokens admin = loginAdmin();
        Fixture f = createFixture(admin);

        JsonNode result = data(adminPost(admin, AUTO_JUDGE_URL, json("assignRecommended", false))
                .andExpect(status().isOk()));
        assertThat(result.path("judgedFree").asInt()).isEqualTo(4);
        assertThat(result.path("recommendedAssigned").asInt()).isZero();

        assertThat(getEdition(admin, f.pdNoEditor).path("koreaCopyright").asText()).isEqualTo("FREE");
        JsonNode workA = getWork(admin, f.workA);
        assertThat(workA.path("recommendedEditionId").isNull()).isTrue();
        assertThat(workA.path("status").asText()).isEqualTo("PREPARING");
    }

    // ===== §5-12 되돌리기 =====

    @Test
    void undo_reverts_only_the_auto_judgements_and_closes_downloads() throws Exception {
        Tokens admin = loginAdmin();
        Fixture f = createFixture(admin);
        adminPost(admin, AUTO_JUDGE_URL, json()).andExpect(status().isOk());

        JsonNode undone = data(adminPost(admin, UNDO_URL, json())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true)));
        assertThat(undone.path("reverted").asInt()).isEqualTo(4);

        JsonNode reverted = getEdition(admin, f.pdNoEditor);
        assertThat(reverted.path("koreaCopyright").asText()).isEqualTo("UNKNOWN");
        assertThat(reverted.path("copyrightNote").isNull()).isTrue();
        assertThat(reverted.path("copyrightJudgedBy").isNull()).isTrue();
        assertThat(reverted.path("copyrightJudgedAt").isNull()).isTrue();
        assertThat(reverted.path("downloadable").asBoolean()).isFalse();

        // 추천 지정은 남지만 판정이 UNKNOWN 이라 다운로드는 닫힌다
        JsonNode workA = getWork(admin, f.workA);
        assertThat(workA.path("recommendedEditionId").asLong()).isEqualTo(f.pdNoEditor);
        assertThat(workA.path("status").asText()).isEqualTo("UNKNOWN");

        // 사람이 판정한 것은 되돌리지 않는다
        adminPut(admin, "/api/admin/editions/{id}/copyright",
                json("koreaCopyright", "FREE", "copyrightNote", "사람이 확인함"), f.pdNoEditor)
                .andExpect(status().isOk());
        adminPost(admin, AUTO_JUDGE_URL, json()).andExpect(status().isOk());

        JsonNode second = data(adminPost(admin, UNDO_URL, json()).andExpect(status().isOk()));
        assertThat(second.path("reverted").asInt()).isEqualTo(3);
        JsonNode kept = getEdition(admin, f.pdNoEditor);
        assertThat(kept.path("koreaCopyright").asText()).isEqualTo("FREE");
        assertThat(kept.path("copyrightJudgedBy").asText()).isEqualTo(ADMIN_USERNAME);
        assertThat(kept.path("copyrightNote").asText()).isEqualTo("사람이 확인함");

        // 되돌릴 것이 없어도 200
        assertThat(data(adminPost(admin, UNDO_URL, json()).andExpect(status().isOk()))
                .path("reverted").asInt()).isZero();
    }

    // ===== §5-8 대기함에 "왜 안 열렸는지" =====

    @Test
    void pending_inbox_shows_why_each_edition_was_not_opened_automatically() throws Exception {
        Tokens admin = loginAdmin();
        Fixture f = createFixture(admin);

        Map<Long, String> reasonByEdition = new LinkedHashMap<>();
        JsonNode pending = data(adminQuery(admin, "/api/admin/copyright/pending", "size", "200")
                .andExpect(status().isOk()));
        for (JsonNode row : pending.path("editions").path("content")) {
            JsonNode reason = row.path("autoJudgeSkipReason");
            reasonByEdition.put(row.path("editionId").asLong(), reason.isNull() ? null : reason.asText());
        }

        assertThat(reasonByEdition).hasSize(10);
        assertThat(reasonByEdition.get(f.editorUnverifiable)).isEqualTo("EDITOR_UNVERIFIABLE");
        assertThat(reasonByEdition.get(f.publicationTooRecent)).isEqualTo("PUBLICATION_TOO_RECENT");
        assertThat(reasonByEdition.get(f.nonRedistributableLicense)).isEqualTo("LICENSE_NOT_REDISTRIBUTABLE");
        assertThat(reasonByEdition.get(f.noLicenseText)).isEqualTo("LICENSE_NOT_REDISTRIBUTABLE");
        assertThat(reasonByEdition.get(f.composerDeathYearUnknown)).isEqualTo("COMPOSER_DEATH_YEAR_UNKNOWN");
        assertThat(reasonByEdition.get(f.composerCopyrightActive)).isEqualTo("COMPOSER_COPYRIGHT_ACTIVE");
        // 자동으로 열릴 수 있는 판본은 사유가 없다
        assertThat(reasonByEdition.get(f.pdNoEditor)).isNull();
        assertThat(reasonByEdition.get(f.ccByShareAlike)).isNull();
        assertThat(reasonByEdition.get(f.pdOldPublication)).isNull();
    }

    // ===== §5-2/§5-3 imslpLicenseCode 는 서버가 도출한다 =====

    @Test
    void saving_an_edition_derives_imslp_license_code_from_the_copyright_text() throws Exception {
        Tokens admin = loginAdmin();
        long workId = createWork(admin, createComposer(admin));

        long editionId = infoEdition(admin, workId, json("imslpCopyrightText", "Public Domain"));
        assertThat(getEdition(admin, editionId).path("imslpLicenseCode").asText()).isEqualTo("PD");

        Map<String, Object> update = editionBody(null, null, null, "UNKNOWN", null);
        update.put("imslpCopyrightText", "Creative Commons Attribution-ShareAlike 4.0");
        JsonNode updated = data(adminPut(admin, "/api/admin/editions/{id}", update, editionId)
                .andExpect(status().isOk()));
        assertThat(updated.path("imslpLicenseCode").asText()).isEqualTo("CC_BY_SA");

        Map<String, Object> cleared = editionBody(null, null, null, "UNKNOWN", null);
        cleared.put("imslpCopyrightText", null);
        assertThat(data(adminPut(admin, "/api/admin/editions/{id}", cleared, editionId).andExpect(status().isOk()))
                .path("imslpLicenseCode").isNull()).isTrue();
    }

    // ===== 인가 (§0-3) =====

    @Test
    void auto_judge_endpoints_require_admin() throws Exception {
        mockMvc.perform(post(AUTO_JUDGE_URL).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("NOT_AUTHENTICATED"));
        mockMvc.perform(post(UNDO_URL).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("NOT_AUTHENTICATED"));

        Tokens user = loginUser();
        mockMvc.perform(post(AUTO_JUDGE_URL)
                        .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
        mockMvc.perform(post(UNDO_URL)
                        .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    }

    // ===== 픽스처 =====

    /**
     * 부록 A §A-1 의 8가지 결과(FREE 3종 + 유지 5종)를 한 번에 만드는 판본 10개.
     *
     * <ul>
     *   <li>곡 A(사후 200년 작곡가): FREE 3건 + 유지 4건 — 추천 자동 지정 대상</li>
     *   <li>곡 D(같은 작곡가): 파일 있는 FREE 악장 발췌 1건만 — 추천 대상이 아님을 확인</li>
     *   <li>곡 B(사망 연도 없는 작곡가) / 곡 C(최근 사망 작곡가): 각 1건</li>
     * </ul>
     */
    private Fixture createFixture(Tokens admin) throws Exception {
        String id = uniq();

        Map<String, Object> longDead = composerBody("테스트오래전작곡가" + id, "Testlongdead, A " + id);
        longDead.put("birthYear", LONG_DEAD - 50);
        longDead.put("deathYear", LONG_DEAD);
        long longDeadId = createComposer(admin, longDead);

        Map<String, Object> unknownDeath = composerBody("테스트사망미상" + id, "Testunknowndeath, B " + id);
        unknownDeath.put("birthYear", null);
        unknownDeath.put("deathYear", null);
        long unknownDeathId = createComposer(admin, unknownDeath);

        Map<String, Object> recentDead = composerBody("테스트최근사망" + id, "Testrecentdead, C " + id);
        recentDead.put("birthYear", RECENTLY_DEAD - 80);
        recentDead.put("deathYear", RECENTLY_DEAD);
        long recentDeadId = createComposer(admin, recentDead);

        long workA = createWork(admin, workBody(longDeadId, "테스트 곡 A " + id, "Test Work A " + id));
        long workD = createWork(admin, workBody(longDeadId, "테스트 곡 D " + id, "Test Work D " + id));
        long workB = createWork(admin, workBody(unknownDeathId, "테스트 곡 B " + id, "Test Work B " + id));
        long workC = createWork(admin, workBody(recentDeadId, "테스트 곡 C " + id, "Test Work C " + id));

        // 곡 A — 파일 있는 전곡 판본(추천 후보가 되는 것들)
        long pdNoEditor = fileEdition(admin, workA,
                json("imslpCopyrightText", "Public Domain", "editor", null, "arranger", null, "publishYear", null));
        long pdOldPublication = fileEdition(admin, workA,
                json("imslpCopyrightText", "Public Domain", "editor", "Sigmund Lebert",
                        "publishYear", OLD_PUBLICATION));
        // 파일 없는 판본들 (판정은 되지만 추천 후보가 아니다)
        long ccByShareAlike = infoEdition(admin, workA,
                json("imslpCopyrightText", "Creative Commons Attribution-ShareAlike 4.0",
                        "editor", "Typesetter Person", "publishYear", RECENT_PUBLICATION));
        long editorUnverifiable = infoEdition(admin, workA,
                json("imslpCopyrightText", "Public Domain", "editor", "Ignacy Paderewski",
                        "publishYear", MIDDLE_PUBLICATION));
        long publicationTooRecent = infoEdition(admin, workA,
                json("imslpCopyrightText", "Public Domain", "editor", null, "arranger", null,
                        "publishYear", RECENT_PUBLICATION));
        long nonRedistributableLicense = infoEdition(admin, workA,
                json("imslpCopyrightText", "Creative Commons Attribution-NonCommercial-NoDerivatives 4.0",
                        "editor", null, "arranger", null, "publishYear", null));
        long noLicenseText = infoEdition(admin, workA,
                json("imslpCopyrightText", null, "editor", null, "arranger", null, "publishYear", null));

        // 곡 D — 파일은 있으나 악장 발췌라 추천 후보가 아니다
        long movementFree = fileEdition(admin, workD,
                json("imslpCopyrightText", "Public Domain", "editor", null, "arranger", null, "publishYear", null,
                        "scope", "MOVEMENT", "movementNumber", 1));

        long composerDeathYearUnknown = infoEdition(admin, workB,
                json("imslpCopyrightText", "Public Domain", "editor", null, "arranger", null, "publishYear", null));
        long composerCopyrightActive = infoEdition(admin, workC,
                json("imslpCopyrightText", "Public Domain", "editor", null, "arranger", null, "publishYear", null));

        return new Fixture(workA, workD, pdNoEditor, pdOldPublication, ccByShareAlike, movementFree,
                editorUnverifiable, publicationTooRecent, nonRedistributableLicense, noLicenseText,
                composerDeathYearUnknown, composerCopyrightActive);
    }

    private record Fixture(long workA, long workMovementOnly,
                           long pdNoEditor, long pdOldPublication, long ccByShareAlike, long movementFree,
                           long editorUnverifiable, long publicationTooRecent,
                           long nonRedistributableLicense, long noLicenseText,
                           long composerDeathYearUnknown, long composerCopyrightActive) {
    }

    /** 샘플 PDF 를 붙인 판정 UNKNOWN 판본. {@code extra} 로 판정 근거 필드를 덮어쓴다. */
    private long fileEdition(Tokens admin, long workId, Map<String, Object> extra) throws Exception {
        JsonNode upload = uploadSamplePdf(admin);
        Map<String, Object> body = editionBody(upload.path("fileId").asLong(),
                upload.path("previewFileId").asLong(), upload.path("pageCount").asInt(), "UNKNOWN", null);
        body.putAll(extra);
        return createEdition(admin, workId, body);
    }

    /** 파일 없는 판정 UNKNOWN 판본. */
    private long infoEdition(Tokens admin, long workId, Map<String, Object> extra) throws Exception {
        Map<String, Object> body = editionBody(null, null, null, "UNKNOWN", null);
        body.putAll(extra);
        return createEdition(admin, workId, body);
    }

    // ===== 응답 배열 유틸 =====

    private java.util.List<String> names(JsonNode array, String field) {
        java.util.List<String> list = new java.util.ArrayList<>();
        array.forEach(node -> list.add(node.path(field).asText()));
        return list;
    }

    private Map<String, Integer> counts(JsonNode array, String field) {
        Map<String, Integer> map = new LinkedHashMap<>();
        array.forEach(node -> map.put(node.path(field).asText(), node.path("count").asInt()));
        return map;
    }

    private int sum(JsonNode array) {
        int total = 0;
        for (JsonNode node : array) {
            total += node.path("count").asInt();
        }
        return total;
    }
}
