package com.test.test.integration;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * WorkSummaryDTO.scopeNote — 02 §2-2-1 (기획 01 §11-2 ②, §10-3).
 *
 * <p>"받게 되는 악보가 찾은 것과 어떻게 다른가" 를 한 줄로 만들 재료. 묶음("더 크다")과 편곡·악장("작거나 다르다")을
 * <b>한 필드</b>로 통합한다. 할 말이 없으면 필드가 통째로 null 이고, codes 는 절대 빈 배열이 아니다.
 *
 * <p>곡 상세의 수록곡 안내(collectionGuide)는 시드가 넣는 <b>데이터</b>이며 COLLECTION 판정의 근거다.
 */
class WorkScopeNoteIntegrationTest extends SheetMusicFixtureSupport {

    @Test
    @DisplayName("전체 악보 · 전곡이면 scopeNote 는 null — 대부분의 곡에 줄이 늘어나지 않는다")
    void completeScore_hasNoScopeNote() throws Exception {
        Tokens admin = loginAdmin();
        long composerId = createComposer(admin, "범위없음", "Scopenone, Zz");
        ReadyWork rw = createReadyWork(admin, composerId, "zz범위 전곡", "Zz Complete Work", 10);

        JsonNode item = searchItem("zz범위 전곡", rw.workId());
        assertThat(item.path("scopeNote").isNull()).isTrue();
    }

    @Test
    @DisplayName("추천 판본이 편곡이면 codes = [ARRANGEMENT]")
    void arrangementRecommendation_marksArrangement() throws Exception {
        Tokens admin = loginAdmin();
        long composerId = createComposer(admin, "범위편곡", "Scopearr, Zz");
        long workId = recommendedWork(admin, composerId, "zz범위 편곡곡", "Zz Arranged Work",
                Map.of("kind", "ARRANGEMENT", "arranger", "Franz Liszt"));

        JsonNode scopeNote = searchItem("zz범위 편곡곡", workId).path("scopeNote");
        assertThat(strings(scopeNote.path("codes"))).containsExactly("ARRANGEMENT");
        assertThat(scopeNote.path("movementNumber").isNull()).isTrue();
    }

    @Test
    @DisplayName("추천 판본이 특정 악장이면 codes = [MOVEMENT_ONLY] + movementNumber")
    void movementOnlyRecommendation_marksMovementWithNumber() throws Exception {
        Tokens admin = loginAdmin();
        long composerId = createComposer(admin, "범위악장", "Scopemov, Zz");
        long workId = recommendedWork(admin, composerId, "zz범위 악장곡", "Zz Movement Work",
                Map.of("scope", "MOVEMENT", "movementNumber", 2));

        JsonNode scopeNote = searchItem("zz범위 악장곡", workId).path("scopeNote");
        assertThat(strings(scopeNote.path("codes"))).containsExactly("MOVEMENT_ONLY");
        assertThat(scopeNote.path("movementNumber").asInt()).isEqualTo(2);
    }

    @Test
    @DisplayName("편곡이면서 특정 악장이면 고정 순서로 둘 다: [ARRANGEMENT, MOVEMENT_ONLY]")
    void bothCodes_comeBackInFixedOrder() throws Exception {
        Tokens admin = loginAdmin();
        long composerId = createComposer(admin, "범위둘다", "Scopeboth, Zz");
        long workId = recommendedWork(admin, composerId, "zz범위 둘다곡", "Zz Both Work",
                Map.of("kind", "ARRANGEMENT", "scope", "MOVEMENT", "movementNumber", 3));

        JsonNode scopeNote = searchItem("zz범위 둘다곡", workId).path("scopeNote");
        assertThat(strings(scopeNote.path("codes"))).containsExactly("ARRANGEMENT", "MOVEMENT_ONLY");
        assertThat(scopeNote.path("movementNumber").asInt()).isEqualTo(3);
    }

    @Test
    @DisplayName("추천 판본이 없으면 판단 근거가 없다 — scopeNote 는 null")
    void withoutRecommendedEdition_thereIsNothingToSay() throws Exception {
        Tokens admin = loginAdmin();
        long composerId = createComposer(admin, "범위추천없음", "Scopenorec, Zz");
        long workId = createWork(admin, composerId, "zz범위 준비중곡", "Zz Preparing Work");

        JsonNode item = searchItem("zz범위 준비중곡", workId);
        assertThat(item.path("status").asText()).isEqualTo("PREPARING");
        assertThat(item.path("scopeNote").isNull()).isTrue();
    }

    @Test
    @DisplayName("같은 값이 작곡가 상세 곡 목록에도 온다 (WorkSummaryDTO 를 쓰는 모든 응답)")
    void sameValue_inComposerWorkList() throws Exception {
        Tokens admin = loginAdmin();
        long composerId = createComposer(admin, "범위작곡가", "Scopecomposer, Zz");
        long workId = recommendedWork(admin, composerId, "zz범위 작곡가곡", "Zz Composer Scope Work",
                Map.of("scope", "MOVEMENT", "movementNumber", 1));

        JsonNode works = getJson("/api/composers/{id}/works", composerId).path("data").path("works").path("content");
        JsonNode item = findById(works, workId);
        assertThat(strings(item.path("scopeNote").path("codes"))).containsExactly("MOVEMENT_ONLY");
        assertThat(item.path("scopeNote").path("movementNumber").asInt()).isEqualTo(1);
    }

    @Test
    @DisplayName("묶음 악보(시드 수록곡 안내가 있는 곡)는 codes = [COLLECTION], 곡 상세에 안내 문장이 보인다")
    void collectionWork_isMarkedFromSeedCollectionGuide() throws Exception {
        long workId = findSeedWorkId("강아지 왈츠", "Waltzes, Op.64");

        JsonNode item = searchItem("강아지 왈츠", workId);
        assertThat(strings(item.path("scopeNote").path("codes"))).containsExactly("COLLECTION");
        assertThat(item.path("scopeNote").path("movementNumber").isNull()).isTrue();
        assertThat(item.path("matchedAlias").asText()).isEqualTo("강아지 왈츠");

        JsonNode detail = getJson("/api/works/{id}", workId).path("data");
        assertThat(detail.path("collectionGuide").asText())
                .startsWith("이 악보에는 왈츠 3곡이 들어 있어요")
                .contains("1번");
    }

    @Test
    @DisplayName("단일 곡 시드는 수록곡 안내가 없고 scopeNote 도 null")
    void singlePieceSeedWork_hasNoCollectionGuide() throws Exception {
        long workId = findSeedWorkId("엘리제를 위하여", "Für Elise, WoO 59");

        assertThat(searchItem("엘리제를 위하여", workId).path("scopeNote").isNull()).isTrue();
        assertThat(getJson("/api/works/{id}", workId).path("data").path("collectionGuide").isNull()).isTrue();
    }

    // ===== 헬퍼 =====

    /** 파일 있는 FREE 판본 1개를 만들어 추천으로 지정한 곡. {@code overrides} 로 kind/scope 를 바꾼다. */
    private long recommendedWork(Tokens admin, long composerId, String titleKo, String titleOriginal,
                                 Map<String, Object> overrides) throws Exception {
        long workId = createWork(admin, composerId, titleKo, titleOriginal);
        long editionId = createEdition(admin, workId, uploadSamplePdf(admin), 5, "FREE", overrides);
        recommendEdition(admin, workId, editionId);
        return workId;
    }

    private JsonNode searchItem(String q, long workId) throws Exception {
        return findById(searchWorks(q), workId);
    }

    private JsonNode findById(JsonNode array, long workId) {
        for (JsonNode item : array) {
            if (item.path("id").asLong() == workId) {
                return item;
            }
        }
        throw new AssertionError("응답에 곡 " + workId + " 이 없다");
    }

    private List<String> strings(JsonNode array) {
        List<String> values = new ArrayList<>();
        array.forEach(node -> values.add(node.asText()));
        return values;
    }
}
