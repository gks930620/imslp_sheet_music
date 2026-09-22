package com.test.test.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.test.test.integration.support.AdminApiTestSupport;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * "이 판본을 고른 이유" 와 "바뀐 이력" — 02 §4-7-2 · §5-5 · §5-6 (01_ERD §3-13, 기획 06 §1·§5).
 *
 * <p>추천이 정해지는 순간은 셋이고(기획 §1-2) <b>셋 전부가 한 줄을 남긴다</b>: 자동 지정 · 관리자 지정 · 추천이 빠짐.
 * 이 파일은 <b>사람이 만드는 두 순간</b>(지정·빠짐)과 이력이 쌓이는 방식을 잠근다 — 자동은
 * {@code RecommendationAutoEvidenceIntegrationTest}.
 *
 * <p>가장 놓치기 쉬운 계약 둘:
 * <ul>
 *   <li><b>이력은 덮이지 않는다</b>(8-D 1). 두 번 바꾸면 두 줄이고 앞 줄이 그대로 남는다.</li>
 *   <li><b>근거가 비어 있는 상태가 정상이다</b>(8-E 1, 기획 §5). 실데이터 42곡은 근거가 없고,
 *       서버가 지금 값으로 지어내면 그건 그때의 판단이 아니라 오늘 다시 낸 답이다.</li>
 * </ul>
 */
class RecommendationLogIntegrationTest extends AdminApiTestSupport {

    // ===== 사람이 지정한 순간 =====

    @Test
    @DisplayName("관리자가 지정하면 누가·언제·무슨 사유로가 한 줄 남는다 (8-A 1·4)")
    void adminAssignmentIsRecorded() throws Exception {
        Tokens admin = loginAdmin();
        long workId = createWork(admin, createComposer(admin));
        long editionId = fileEdition(admin, workId, "Peters", "Köhler", 1880);

        setRecommended(admin, workId, recommendBody(editionId, "NOT_THIS_WORK", "앞 추천은 관현악 총보였음", null));

        JsonNode current = recommendation(admin, workId).path("current");
        assertThat(current.path("source").asText()).isEqualTo("ADMIN");
        assertThat(current.path("action").asText()).isEqualTo("ASSIGNED");
        assertThat(current.path("decidedByNickname").asText()).isEqualTo("한창희");
        assertThat(current.path("decidedAt").isTextual()).isTrue();
        assertThat(current.path("reason").asText()).isEqualTo("NOT_THIS_WORK");
        assertThat(current.path("note").asText()).isEqualTo("앞 추천은 관현악 총보였음");
        assertThat(current.path("auto").isNull()).isTrue();
        assertThat(current.path("clearedReason").isNull()).isTrue();
        // 처음 지정이면 이전 추천이 없다 → 화면은 "처음 지정한 추천이에요"
        assertThat(current.path("previousEdition").isNull()).isTrue();
    }

    @Test
    @DisplayName("로그인 아이디가 아니라 닉네임이 온다 — 관리자 계정 정보를 화면에 흘리지 않는다")
    void nicknameNotUsername() throws Exception {
        Tokens admin = loginAdmin();
        long workId = createWork(admin, createComposer(admin));
        setRecommended(admin, workId, recommendBody(fileEdition(admin, workId, "Peters", null, 1880),
                "BETTER_READABILITY", null, null));

        JsonNode current = recommendation(admin, workId).path("current");
        assertThat(current.path("decidedByNickname").asText()).isNotEqualTo(ADMIN_USERNAME);
        assertThat(current.toString()).doesNotContain(ADMIN_USERNAME);
    }

    @Test
    @DisplayName("이전 추천이 그때의 표기로 함께 남는다 — 지금 판본을 다시 읽은 값이 아니다")
    void previousEditionIsASnapshotOfItsLabelAtThatTime() throws Exception {
        Tokens admin = loginAdmin();
        long workId = createWork(admin, createComposer(admin));
        long first = fileEdition(admin, workId, "Breitkopf", "Lebert", 1862);
        long second = fileEdition(admin, workId, "Peters", "Köhler", 1880);

        setRecommended(admin, workId, recommendBody(first, "BETTER_READABILITY", null, null));
        setRecommended(admin, workId, recommendBody(second, "NOT_THIS_WORK", null, null));

        JsonNode previous = recommendation(admin, workId).path("current").path("previousEdition");
        assertThat(previous.path("editionId").asLong()).isEqualTo(first);
        assertThat(previous.path("publisher").asText()).isEqualTo("Breitkopf");
        assertThat(previous.path("editor").asText()).isEqualTo("Lebert");
        assertThat(previous.path("publishYear").asInt()).isEqualTo(1862);
        assertThat(previous.path("kind").asText()).isEqualTo("COMPLETE_SCORE");
        assertThat(previous.path("scope").asText()).isEqualTo("COMPLETE");

        // 앞 판본의 표기를 나중에 고쳐도 이력의 그 줄은 그때 값을 그대로 말한다
        Map<String, Object> edit = editionSaveBodyFrom(getEdition(admin, first));
        edit.put("publisher", "고쳐 쓴 출판사");
        adminPut(admin, "/api/admin/editions/{id}", edit, first).andExpect(status().isOk());

        assertThat(recommendation(admin, workId).path("current").path("previousEdition").path("publisher").asText())
                .isEqualTo("Breitkopf");
    }

    @Test
    @DisplayName("같은 판본을 다시 지정하면 줄이 늘지 않는다 — 추천이 바뀌지 않았으면 정해진 순간도 아니다")
    void reAssigningTheSameEditionDoesNotAddALine() throws Exception {
        Tokens admin = loginAdmin();
        long workId = createWork(admin, createComposer(admin));
        long editionId = fileEdition(admin, workId, "Peters", "Köhler", 1880);

        setRecommended(admin, workId, recommendBody(editionId, "BETTER_READABILITY", null, null));
        setRecommended(admin, workId, recommendBody(editionId, "BETTER_FOR_LEARNERS", null, null));

        assertThat(recommendation(admin, workId).path("historyCount").asInt()).isEqualTo(1);
        assertThat(recommendation(admin, workId).path("current").path("reason").asText())
                .isEqualTo("BETTER_READABILITY");
    }

    // ===== 이력은 쌓인다 =====

    @Test
    @DisplayName("두 번 바꾸면 이력이 최신순 3줄이고 앞 줄이 덮이지 않는다 (8-D 1·2)")
    void historyAccumulatesNewestFirst() throws Exception {
        Tokens admin = loginAdmin();
        long workId = createWork(admin, createComposer(admin));
        long a = fileEdition(admin, workId, "Schirmer", null, 1901);
        long b = fileEdition(admin, workId, "Breitkopf", "Lebert", 1862);
        long c = fileEdition(admin, workId, "Peters", "Köhler", 1880);

        setRecommended(admin, workId, recommendBody(a, "BETTER_READABILITY", null, null));
        setRecommended(admin, workId, recommendBody(b, "BETTER_FOR_LEARNERS", null, null));
        setRecommended(admin, workId, recommendBody(c, "NOT_THIS_WORK", null, null));

        JsonNode recommendation = recommendation(admin, workId);
        assertThat(recommendation.path("historyCount").asInt()).isEqualTo(3);
        assertThat(recommendation.path("hasMore").asBoolean()).isFalse();

        JsonNode history = recommendation.path("history");
        assertThat(history.size()).isEqualTo(3);
        // 맨 위가 지금 근거다 — current 와 같은 줄
        assertThat(history.get(0).path("id").asLong()).isEqualTo(recommendation.path("current").path("id").asLong());
        assertThat(strings(collect(history, "reason")))
                .containsExactly("NOT_THIS_WORK", "BETTER_FOR_LEARNERS", "BETTER_READABILITY");
        // 한 줄에 "어느 판본에서 어느 판본으로" 가 있다 (8-D 2)
        assertThat(history.get(1).path("previousEdition").path("editionId").asLong()).isEqualTo(a);
        assertThat(history.get(1).path("edition").path("editionId").asLong()).isEqualTo(b);
        assertThat(history.get(2).path("previousEdition").isNull()).isTrue();
    }

    @Test
    @DisplayName("곡 상세는 최근 5줄까지만 싣고 hasMore 로 더 있다고 말한다 (§4-7-2)")
    void workDetailCarriesAtMostFiveLines() throws Exception {
        Tokens admin = loginAdmin();
        long workId = createWork(admin, createComposer(admin));
        long[] editions = new long[6];
        for (int i = 0; i < editions.length; i++) {
            editions[i] = fileEdition(admin, workId, "출판사" + i, null, 1900 + i);
            setRecommended(admin, workId, recommendBody(editions[i], "BETTER_READABILITY", null, null));
        }

        JsonNode recommendation = recommendation(admin, workId);
        assertThat(recommendation.path("historyCount").asInt()).isEqualTo(6);
        assertThat(recommendation.path("history").size()).isEqualTo(5);
        assertThat(recommendation.path("hasMore").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("한 번만 지정된 곡은 이력 1줄 — 화면이 접힘 헤더를 만들지 않을 수 있게 수를 준다 (8-D 5)")
    void singleAssignmentHasCountOne() throws Exception {
        Tokens admin = loginAdmin();
        long workId = createWork(admin, createComposer(admin));
        setRecommended(admin, workId, recommendBody(fileEdition(admin, workId, "Peters", null, 1880),
                "BETTER_READABILITY", null, null));

        JsonNode recommendation = recommendation(admin, workId);
        assertThat(recommendation.path("historyCount").asInt()).isEqualTo(1);
        assertThat(recommendation.path("history").size()).isEqualTo(1);
        assertThat(recommendation.path("hasMore").asBoolean()).isFalse();
    }

    // ===== 추천이 빠지는 순간 (8-B 7) =====

    @Test
    @DisplayName("추천 판본을 삭제하면 '추천을 뺐어요' 한 줄이 남고, 무엇이 빠졌는지가 스냅샷으로 남는다")
    void deletingTheRecommendedEditionRecordsAClearedLine() throws Exception {
        Tokens admin = loginAdmin();
        long workId = createWork(admin, createComposer(admin));
        long editionId = fileEdition(admin, workId, "Peters", "Köhler", 1880);
        setRecommended(admin, workId, recommendBody(editionId, "BETTER_READABILITY", null, null));

        adminDelete(admin, "/api/admin/editions/{id}", editionId).andExpect(status().isNoContent());

        JsonNode recommendation = recommendation(admin, workId);
        // 추천이 없으므로 "고른 이유" 는 없다 — 그래도 이력은 남는다(화면정의 06 A-0)
        assertThat(recommendation.path("current").isNull()).isTrue();
        assertThat(recommendation.path("historyCount").asInt()).isEqualTo(2);

        JsonNode cleared = recommendation.path("history").get(0);
        assertThat(cleared.path("action").asText()).isEqualTo("CLEARED");
        assertThat(cleared.path("source").asText()).isEqualTo("ADMIN");
        assertThat(cleared.path("decidedByNickname").asText()).isEqualTo("한창희");
        assertThat(cleared.path("clearedReason").asText()).isEqualTo("EDITION_DELETED");
        assertThat(cleared.path("edition").isNull()).isTrue();
        assertThat(cleared.path("reason").isNull()).isTrue();
        // 판본은 사라졌지만 무엇이 빠졌는지는 말할 수 있어야 한다
        assertThat(cleared.path("previousEdition").path("editionId").isNull()).isTrue();
        assertThat(cleared.path("previousEdition").path("publisher").asText()).isEqualTo("Peters");
        assertThat(cleared.path("previousEdition").path("editor").asText()).isEqualTo("Köhler");
        assertThat(cleared.path("previousEdition").path("publishYear").asInt()).isEqualTo(1880);

        // 앞 줄(지정)도 그대로 남고, 지워진 판본을 가리키던 id 만 비었다
        JsonNode assigned = recommendation.path("history").get(1);
        assertThat(assigned.path("action").asText()).isEqualTo("ASSIGNED");
        assertThat(assigned.path("edition").path("editionId").isNull()).isTrue();
        assertThat(assigned.path("edition").path("publisher").asText()).isEqualTo("Peters");
    }

    @Test
    @DisplayName("추천이 아닌 판본을 지우면 줄이 늘지 않는다 — 추천이 정해진 순간이 아니다")
    void deletingANonRecommendedEditionRecordsNothing() throws Exception {
        Tokens admin = loginAdmin();
        long workId = createWork(admin, createComposer(admin));
        long recommended = fileEdition(admin, workId, "Peters", "Köhler", 1880);
        long other = fileEdition(admin, workId, "Schirmer", null, 1901);
        setRecommended(admin, workId, recommendBody(recommended, "BETTER_READABILITY", null, null));

        adminDelete(admin, "/api/admin/editions/{id}", other).andExpect(status().isNoContent());

        assertThat(recommendation(admin, workId).path("historyCount").asInt()).isEqualTo(1);
        assertThat(recommendation(admin, workId).path("current").path("action").asText()).isEqualTo("ASSIGNED");
    }

    @Test
    @DisplayName("추천 판본에서 파일을 떼도 추천이 풀리고 그 사실이 남는다 (§5-3 — 계약에 없으면 이 길만 이력이 빈다)")
    void removingTheFileOfTheRecommendedEditionRecordsAClearedLine() throws Exception {
        Tokens admin = loginAdmin();
        long workId = createWork(admin, createComposer(admin));
        long editionId = fileEdition(admin, workId, "Peters", "Köhler", 1880);
        setRecommended(admin, workId, recommendBody(editionId, "BETTER_READABILITY", null, null));

        Map<String, Object> withoutFile = editionSaveBodyFrom(getEdition(admin, editionId));
        withoutFile.put("fileId", null);
        withoutFile.put("previewFileId", null);
        adminPut(admin, "/api/admin/editions/{id}", withoutFile, editionId).andExpect(status().isOk());

        JsonNode recommendation = recommendation(admin, workId);
        assertThat(getWork(admin, workId).path("recommendedEditionId").isNull()).isTrue();
        assertThat(recommendation.path("current").isNull()).isTrue();
        assertThat(recommendation.path("historyCount").asInt()).isEqualTo(2);

        JsonNode cleared = recommendation.path("history").get(0);
        assertThat(cleared.path("action").asText()).isEqualTo("CLEARED");
        assertThat(cleared.path("clearedReason").asText()).isEqualTo("EDITION_FILE_REMOVED");
        // 판본 자체는 살아 있다 — 파일만 떼어 냈다
        assertThat(cleared.path("previousEdition").path("editionId").asLong()).isEqualTo(editionId);
    }

    // ===== 근거가 비어 있는 상태가 정상이다 (기획 §5) =====

    @Test
    @DisplayName("추천은 있는데 기록이 없는 곡(실데이터 42곡)은 current=null · history=[] 다 (8-E 1)")
    void anExistingRecommendationWithoutAnyRecordIsNormal() throws Exception {
        Tokens admin = loginAdmin();
        long workId = createWork(admin, createComposer(admin));
        long editionId = makeReady(admin, workId);
        clearRecommendationLog(workId);

        JsonNode recommendation = recommendation(admin, workId);
        assertThat(recommendation.isObject()).isTrue();          // 빈 상태는 하나뿐 — 객체는 항상 있다
        assertThat(recommendation.path("current").isNull()).isTrue();
        assertThat(recommendation.path("history").isArray()).isTrue();
        assertThat(recommendation.path("history").size()).isZero();
        assertThat(recommendation.path("historyCount").asInt()).isZero();
        assertThat(recommendation.path("hasMore").asBoolean()).isFalse();
        // 추천 자체는 그대로다 — 기록이 없다고 추천이 흔들리면 안 된다 (8-E 2)
        assertThat(getWork(admin, workId).path("recommendedEditionId").asLong()).isEqualTo(editionId);
    }

    @Test
    @DisplayName("기록 없는 곡을 한 번 다시 지정하면 그때부터 정상적으로 근거가 생긴다 (8-E 3)")
    void reAssigningAnUnrecordedWorkStartsTheRecord() throws Exception {
        Tokens admin = loginAdmin();
        long workId = createWork(admin, createComposer(admin));
        long old = makeReady(admin, workId);
        clearRecommendationLog(workId);
        long fresh = fileEdition(admin, workId, "Peters", "Köhler", 1880);

        setRecommended(admin, workId, recommendBody(fresh, "NOT_THIS_WORK", null, null));

        JsonNode current = recommendation(admin, workId).path("current");
        assertThat(current.path("reason").asText()).isEqualTo("NOT_THIS_WORK");
        // 이력이 0줄이어도 "이전 추천" 은 있었다 — 그 사실을 근거가 말할 수 있어야 한다
        assertThat(current.path("previousEdition").path("editionId").asLong()).isEqualTo(old);
    }

    @Test
    @DisplayName("추천도 기록도 없는 곡도 같은 빈 객체다 — 빈 상태를 두 가지로 만들지 않는다")
    void aWorkWithNoRecommendationAtAllHasTheSameEmptyShape() throws Exception {
        Tokens admin = loginAdmin();
        long workId = createWork(admin, createComposer(admin));

        JsonNode recommendation = recommendation(admin, workId);
        assertThat(recommendation.isObject()).isTrue();
        assertThat(recommendation.path("current").isNull()).isTrue();
        assertThat(recommendation.path("historyCount").asInt()).isZero();
        assertThat(recommendation.path("history").size()).isZero();
    }

    // ===== 도우미 =====

    /** 출판사·편집자·연도를 지정한 파일 있는 FREE 판본 (스냅샷을 눈으로 확인하기 위한 값). */
    private long fileEdition(Tokens admin, long workId, String publisher, String editor, Integer publishYear)
            throws Exception {
        JsonNode upload = uploadSamplePdf(admin);
        Map<String, Object> body = editionBody(upload.path("fileId").asLong(),
                upload.path("previewFileId").asLong(), upload.path("pageCount").asInt(), "FREE", "판정 근거");
        body.put("publisher", publisher);
        body.put("editor", editor);
        body.put("publishYear", publishYear);
        return createEdition(admin, workId, body);
    }

    private JsonNode collect(JsonNode array, String field) {
        com.fasterxml.jackson.databind.node.ArrayNode collected = objectMapper.createArrayNode();
        array.forEach(node -> collected.add(node.path(field)));
        return collected;
    }
}
