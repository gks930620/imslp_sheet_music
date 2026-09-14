package com.test.test.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.test.test.integration.support.AdminApiTestSupport;
import java.time.Year;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 자동 판정 되돌리기의 <b>진입점 계약</b> — 02_API_명세서 §5-8-1 (2026-09-09 신설, senior-dev. qa 5차 결함 1. <b>Red</b>).
 *
 * <h2>계약이 없었다</h2>
 * §5-12 되돌리기 API 는 처음부터 있었는데, 화면은 "자동 판정만 되돌리기" 버튼을 <b>실행 직후의 컴포넌트 로컬 state</b>
 * 로만 그렸다. 관리 홈에 갔다 오거나 새로고침하면 버튼이 사라지고, 재실행해도 멱등이라 {@code judgedFree == 0} 이면
 * 다시 나타나지 않는다. <b>API 는 살아 있는데 진입점이 없는 상태</b>이고 8084 실데이터가 이미 그렇다.
 * 버튼을 상시 노출하려면 화면이 "되돌릴 것이 남아 있나" 를 알아야 하는데 <b>어떤 응답에도 그 값이 없었다</b>
 * (§4-1 {@code unknownCopyrightEditions} 는 UNKNOWN 수라 무관하다).
 *
 * <h2>왜 이게 중요한가</h2>
 * 이 되돌리기는 기획 {@code 02_저작권_판정_지침.md} 부록 A §A-2 ④ 가 "출판 후 120년" 이라는 <b>통계적</b> 안전선을
 * 정당화하는 근거다 — "한 번의 요청으로 전부 되돌릴 수 있게 한다". 수백~수천 판본을 한 번에 공개 재배포로 여는
 * 동작의 지정된 안전장치가 운영 중 도달 불가면, 규칙의 근거가 함께 약해진다.
 *
 * <h2>확정: §5-8 대기함 응답에 {@code autoJudged} 를 싣는다</h2>
 * 되돌리기 버튼이 있는 화면이 대기함이고, 대기함이 이미 하는 한 번의 조회에 실으면 요청이 늘지 않는다.
 * 두 숫자는 §5-12 와 <b>같은 조건</b>으로 세어, "지금 되돌리면 이렇게 된다" 는 예고가 실제 결과와 어긋나지 않게 한다.
 *
 * <h2>두 값은 {@code long} 이다 (2026-09-09 정정, senior-dev)</h2>
 * 출처가 {@code count(*)} 라 리포지터리가 {@code long} 을 준다 — DTO 가 {@code int} 면 서비스에서
 * {@code (int)} 축소 캐스팅이 생기고, 같은 DTO 를 담는 {@code unfilteredTotal} 은 이미 {@code long} 이라
 * 한 응답 안에서 같은 성격의 수가 두 폭으로 갈린다. JSON 은 어느 쪽이든 같으므로 <b>이 테스트의 단언은 바뀌지 않는다</b>
 * (계약 타입을 그대로 읽도록 {@code asLong()} 으로만 맞췄다). §5-12 {@code UndoResult} 는 그대로 {@code int} 다 —
 * 그쪽 수는 이미 메모리에 올린 {@code List.size()} 라 캐스팅이 없다.
 */
class AutoJudgeUndoEntryPointIntegrationTest extends AdminApiTestSupport {

    private static final String AUTO_JUDGE_URL = "/api/admin/copyright/auto-judge";
    private static final String UNDO_URL = "/api/admin/copyright/auto-judge/undo";
    private static final String PENDING_URL = "/api/admin/copyright/pending";

    /** 사후 70년이 한참 지난 작곡가 (경계에서 멀리 떨어뜨려 해가 바뀌어도 결과가 같다). */
    private static final int LONG_DEAD = Year.now().getValue() - 200;

    // ===== 값이 있다 · 항상 있다 =====

    @Test
    @DisplayName("자동 판정을 한 번도 돌리지 않았으면 되돌릴 것이 0 이다 — 그래도 필드는 항상 있다")
    void beforeAnyRun_theCountsArePresentAndZero() throws Exception {
        Tokens admin = loginAdmin();
        createFixture(admin);

        JsonNode autoJudged = autoJudged(admin);
        assertThat(autoJudged.isObject())
                .as("객체는 항상 있다 — 화면이 키 유무로 분기하지 않게(§5-8-1)")
                .isTrue();
        assertThat(autoJudged.path("revertibleEditions").asLong()).isZero();
        assertThat(autoJudged.path("revertibleRecommendedWorks").asLong()).isZero();
    }

    @Test
    @DisplayName("실행한 뒤에는 되돌릴 판본 수와 닫히는 곡 수가 온다 — 화면은 이 값으로 버튼을 그린다")
    void afterRun_theCountsTellWhatCanBeReverted() throws Exception {
        Tokens admin = loginAdmin();
        Fixture f = createFixture(admin);

        JsonNode run = data(adminPost(admin, AUTO_JUDGE_URL, json()));
        assertThat(run.path("judgedFree").asInt()).as("픽스처 확인: 자동으로 열리는 판본 2개").isEqualTo(2);
        assertThat(run.path("recommendedAssigned").asInt()).as("픽스처 확인: 추천이 붙는 곡 1개").isEqualTo(1);

        JsonNode autoJudged = autoJudged(admin);
        assertThat(autoJudged.path("revertibleEditions").asLong())
                .as("copyright_judged_by = 'system:auto' AND FREE 인 판본 수")
                .isEqualTo(2);
        assertThat(autoJudged.path("revertibleRecommendedWorks").asLong())
                .as("그중 어떤 곡의 추천 판본인 것 = 되돌리면 다운로드가 닫히는 곡 수")
                .isEqualTo(1);
        assertThat(getWork(admin, f.recommendedWorkId).path("recommendedEditionId").asLong())
                .isEqualTo(f.freeCompleteEdition);
    }

    // ===== 목록·필터와 무관하다 (버튼이 사라지던 바로 그 상태) =====

    @Test
    @DisplayName("대기 목록이 0건이어도 값은 그대로다 — 전부 자동으로 연 직후가 되돌리기가 가장 필요한 때다")
    void withAnEmptyPendingList_theCountsRemain() throws Exception {
        Tokens admin = loginAdmin();
        Fixture f = createFixture(admin);
        adminPost(admin, AUTO_JUDGE_URL, json());
        // 남은 UNKNOWN 을 사람이 전부 판정해 대기함을 비운다
        for (long editionId : new long[]{f.editorUnverifiable, f.restrictedByHand}) {
            adminPut(admin, "/api/admin/editions/{id}/copyright",
                    json("koreaCopyright", "RESTRICTED", "copyrightNote", "테스트 판정 근거"), editionId);
        }

        JsonNode pending = pending(admin);
        assertThat(pending.path("unfilteredTotal").asLong()).as("대기함이 비었다").isZero();
        assertThat(pending.path("editions").path("content")).isEmpty();
        assertThat(pending.path("autoJudged").path("revertibleEditions").asLong())
                .as("목록이 비어도 되돌릴 것은 남아 있다 — 이때 버튼이 사라지면 진입점이 없어진다")
                .isEqualTo(2);
        assertThat(pending.path("autoJudged").path("revertibleRecommendedWorks").asLong()).isEqualTo(1);
    }

    @Test
    @DisplayName("필터(q·composerId)를 걸어도 값이 변하지 않는다 — 목록의 부분집합이 아니라 화면 전체의 상태다")
    void filtersDoNotChangeTheCounts() throws Exception {
        Tokens admin = loginAdmin();
        createFixture(admin);
        adminPost(admin, AUTO_JUDGE_URL, json());

        JsonNode unfiltered = autoJudged(admin);
        assertThat(unfiltered.path("revertibleEditions").asLong()).isEqualTo(2);

        JsonNode filtered = data(adminQuery(admin, PENDING_URL, "q", "이런곡은없다" + uniq()))
                .path("autoJudged");

        assertThat(filtered.path("revertibleEditions").asLong())
                .isEqualTo(unfiltered.path("revertibleEditions").asLong());
        assertThat(filtered.path("revertibleRecommendedWorks").asLong())
                .isEqualTo(unfiltered.path("revertibleRecommendedWorks").asLong());
    }

    // ===== 부분 되돌리기 · 불변식 =====

    @Test
    @DisplayName("사람이 다시 판정한 만큼 줄어든다 — 부분적으로만 되돌릴 수 있는 상태를 그대로 말한다")
    void aHumanRejudgementShrinksTheCount() throws Exception {
        Tokens admin = loginAdmin();
        Fixture f = createFixture(admin);
        adminPost(admin, AUTO_JUDGE_URL, json());
        assertThat(autoJudged(admin).path("revertibleEditions").asLong()).isEqualTo(2);

        // 자동으로 열린 판본 하나를 관리자가 직접 확인해 다시 판정한다(§5-9) → judgedBy 가 사람이 되어 대상에서 빠진다
        adminPut(admin, "/api/admin/editions/{id}/copyright",
                json("koreaCopyright", "FREE", "copyrightNote", "사람이 확인함"), f.freeInfoEdition);

        JsonNode autoJudged = autoJudged(admin);
        assertThat(autoJudged.path("revertibleEditions").asLong())
                .as("하나는 사람 것이 됐다 — 그래도 남은 하나는 여전히 되돌릴 수 있다")
                .isEqualTo(1);
        assertThat(autoJudged.path("revertibleRecommendedWorks").asLong())
                .as("남은 하나가 추천 판본이다")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("예고와 결과가 같다 — 지금 §5-12 를 부르면 reverted·recommendationKept 가 이 값과 일치한다")
    void thePreviewMatchesTheUndoResult() throws Exception {
        Tokens admin = loginAdmin();
        Fixture f = createFixture(admin);
        adminPost(admin, AUTO_JUDGE_URL, json());
        // 부분 되돌리기 상태에서도 성립해야 한다
        adminPut(admin, "/api/admin/editions/{id}/copyright",
                json("koreaCopyright", "FREE", "copyrightNote", "사람이 확인함"), f.freeInfoEdition);

        JsonNode before = autoJudged(admin);
        JsonNode undone = data(adminPost(admin, UNDO_URL, json()));

        assertThat(undone.path("reverted").asLong())
                .as("§5-8-1 revertibleEditions 와 §5-12 reverted 는 같은 조건으로 센다")
                .isEqualTo(before.path("revertibleEditions").asLong());
        assertThat(undone.path("recommendationKept").asLong())
                .as("§5-8-1 revertibleRecommendedWorks 와 §5-12 recommendationKept 도 같다")
                .isEqualTo(before.path("revertibleRecommendedWorks").asLong());

        JsonNode after = autoJudged(admin);
        assertThat(after.path("revertibleEditions").asLong())
                .as("되돌린 뒤에는 더 되돌릴 것이 없다 → 화면에서 버튼이 사라진다")
                .isZero();
        assertThat(after.path("revertibleRecommendedWorks").asLong()).isZero();
    }

    @Test
    @DisplayName("숨김 곡의 추천 판본도 센다 — 예고와 §5-12 결과가 같은 모집단이어야 한다")
    void hiddenWorksAreCountedTheSameWayUndoCountsThem() throws Exception {
        Tokens admin = loginAdmin();
        Fixture f = createFixture(admin);
        adminPost(admin, AUTO_JUDGE_URL, json());
        setWorkHidden(admin, f.recommendedWorkId, true);

        JsonNode before = autoJudged(admin);
        assertThat(before.path("revertibleRecommendedWorks").asLong())
                .as("숨김이어도 빼지 않는다 — §5-12 recommendationKept 가 빼지 않기 때문이다")
                .isEqualTo(1);
        assertThat(data(adminPost(admin, UNDO_URL, json())).path("recommendationKept").asLong())
                .isEqualTo(before.path("revertibleRecommendedWorks").asLong());
    }

    // ===== 인가 (§0-3) =====

    @Test
    @DisplayName("대기함 조회 그대로 ADMIN 전용이다 — 새 필드가 인가를 바꾸지 않는다")
    void theCountsAreAdminOnly() throws Exception {
        mockMvc.perform(get(PENDING_URL)).andExpect(status().isUnauthorized());

        Tokens user = loginUser();
        mockMvc.perform(get(PENDING_URL).header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
                .andExpect(status().isForbidden());
    }

    // ===== 헬퍼 =====

    private JsonNode pending(Tokens admin) throws Exception {
        return data(adminQuery(admin, PENDING_URL, "size", "200"));
    }

    private JsonNode autoJudged(Tokens admin) throws Exception {
        return pending(admin).path("autoJudged");
    }

    /**
     * 자동으로 <b>열리는</b> 판본 2개 + <b>안 열리는</b> 판본 2개.
     *
     * <ul>
     *   <li>곡 1(사후 200년 작곡가): 파일 있는 전곡·편집자 없음 → FREE + 추천 자동 지정 / 편집자 있는 판본 → EDITOR_UNVERIFIABLE</li>
     *   <li>곡 2(같은 작곡가): 파일 없는 편집자 없음 판본 → FREE(추천 후보 아님) / 사람이 판정할 판본 1개</li>
     * </ul>
     */
    private Fixture createFixture(Tokens admin) throws Exception {
        String id = uniq();
        Map<String, Object> composer = composerBody("되돌리기작곡가" + id, "Undotest, A " + id);
        composer.put("birthYear", LONG_DEAD - 50);
        composer.put("deathYear", LONG_DEAD);
        long composerId = createComposer(admin, composer);

        long work1 = createWork(admin, workBody(composerId, "되돌리기 곡 1 " + id, "Undo Work 1 " + id));
        long work2 = createWork(admin, workBody(composerId, "되돌리기 곡 2 " + id, "Undo Work 2 " + id));

        JsonNode upload = uploadSamplePdf(admin);
        Map<String, Object> completeBody = editionBody(upload.path("fileId").asLong(),
                upload.path("previewFileId").asLong(), upload.path("pageCount").asInt(), "UNKNOWN", null);
        completeBody.put("imslpCopyrightText", "Public Domain");
        long freeCompleteEdition = createEdition(admin, work1, completeBody);

        Map<String, Object> withEditor = editionBody(null, null, null, "UNKNOWN", null);
        withEditor.put("imslpCopyrightText", "Public Domain");
        withEditor.put("editor", "Ignacy Paderewski");
        withEditor.put("publishYear", Year.now().getValue() - 90);
        long editorUnverifiable = createEdition(admin, work1, withEditor);

        Map<String, Object> infoFree = editionBody(null, null, null, "UNKNOWN", null);
        infoFree.put("imslpCopyrightText", "Public Domain");
        long freeInfoEdition = createEdition(admin, work2, infoFree);

        Map<String, Object> noLicense = editionBody(null, null, null, "UNKNOWN", null);
        long restrictedByHand = createEdition(admin, work2, noLicense);

        return new Fixture(work1, freeCompleteEdition, freeInfoEdition, editorUnverifiable, restrictedByHand);
    }

    private record Fixture(long recommendedWorkId, long freeCompleteEdition, long freeInfoEdition,
                           long editorUnverifiable, long restrictedByHand) {
    }
}
