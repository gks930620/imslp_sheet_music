package com.test.test.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.test.test.integration.support.AdminApiTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 곡 상세의 "다른 판본" 분량 계약 — 02_API_명세서 §3-3 / 기획 §F3-4 · §10-1 / 화면정의 03 §3-3.
 *
 * <h2>2026-09-08 계약 통일 (qa 4차 결함 4)</h2>
 * 계약과 기획이 서로 다른 말을 하고 있었다.
 * <ul>
 *   <li>계약(2026-09-07): 파일 있는 판본 전부 <b>+ 파일 없는 {@code COMPLETE_SCORE} 최대 5개</b> + {@code otherEditionsTotal}</li>
 *   <li>기획 §F3-4: 줄로 펼치는 것은 <b>파일 있는 판본뿐</b>, 나머지는 "IMSLP 에는 이 곡의 다른 악보가 N개 더 있어요" <b>한 줄</b></li>
 * </ul>
 * <b>기획을 따른다.</b> 근거:
 * <ol>
 *   <li>그 5칸의 유일한 용도가 "IMSLP 에 더 있다" 는 <b>안내</b>인데, 같은 정보를 숫자 한 줄이 더 정확히 전한다.
 *       5줄은 "88개 중 5개" 라는 사실을 말할 수 없다.</li>
 *   <li>그 5줄은 <b>누를 수 없는 줄</b>이다(파일이 없으니 버튼이 "IMSLP에서 보기"). 행동이 다른 줄을 같은 목록에 섞으면
 *       접이식 헤더의 "(N개)" 가 "받을 수 있는 판본 수" 가 아니게 된다 — 헤더 숫자가 거짓말을 한다.</li>
 *   <li>내(senior-dev) 원래 근거는 이미 2026-09-08 편성 필터에서 반쯤 무너졌다. 5칸을 정확하게 만들려고
 *       {@code kind} 를 걸렀지만, 걸러도 "파일 없음" 줄인 것은 같다. 규칙을 덧대는 대신 없앤다.</li>
 *   <li>실측(qa 4차): 프론트는 {@code otherEditionsTotal} 을 아예 쓰지 않는다. 지금 화면에는 파일 없는 5줄만 있고
 *       안내 줄은 없다 — 두 설계의 나쁜 점만 남아 있었다.</li>
 * </ol>
 *
 * <h2>확정 계약</h2>
 * <ul>
 *   <li>{@code otherEditions} = 추천을 뺀 판본 중 <b>파일 있는 것 전부</b>(편성 무관). 상한 없음 —
 *       개수는 우리가 통제한다(곡당 최대 2개, 01 §9-1). 정렬은 그대로 IMSLP 다운로드 수 DESC NULLS LAST, id ASC.</li>
 *   <li>{@code imslpOnlyCount}(신규) = 추천을 뺀 판본 중 <b>파일 없는 것의 수</b>. 화면의 안내 한 줄이 쓰는 값이고,
 *       0 이면 줄을 만들지 않는다. (추천 판본은 파일이 없으면 지정 자체가 400 이므로 §5-6, 이 값은 곧 그 곡의
 *       "우리가 파일을 못 가진 판본 수" 다.)</li>
 *   <li>{@code otherEditionsTotal} <b>삭제</b> — 새 규칙에서는 목록이 잘리지 않아 언제나 {@code otherEditions.length}
 *       와 같다. 길이와 늘 같은 필드는 잡음이고, 없어진 "잘림" 로직을 다시 부른다.</li>
 *   <li>{@code downloadableOtherCount} 는 그대로 <b>전체</b> 기준이다.</li>
 * </ul>
 */
class WorkDetailEditionVolumeIntegrationTest extends AdminApiTestSupport {

    private static final int FILELESS_CREATED = 8;

    @Test
    @DisplayName("판본 11개(파일 3 + 정보만 8): 줄이 되는 것은 파일 있는 2개뿐이고 나머지 8개는 imslpOnlyCount 숫자로만 간다")
    void filelessEditionsBecomeACountNotRows() throws Exception {
        Tokens admin = loginAdmin();
        long workId = createWork(admin, createComposer(admin));

        long recommended = createFileEdition(admin, workId, "FREE", "추천 판정 근거");
        setRecommended(admin, workId, recommended);
        long freeOther = createFileEdition(admin, workId, "FREE", "다른 판본 판정 근거");
        long unknownOther = createFileEdition(admin, workId, "UNKNOWN", null);
        for (int i = 0; i < FILELESS_CREATED; i++) {
            createInfoEdition(admin, workId);
        }

        JsonNode data = data(mockMvc.perform(get("/api/works/{id}", workId)).andExpect(status().isOk()));

        assertThat(data.path("status").asText()).isEqualTo("READY");
        assertThat(data.path("recommendedEdition").path("id").asLong()).isEqualTo(recommended);

        JsonNode others = data.path("otherEditions");
        assertThat(longs(others, "id"))
                .as("파일 있는 판본만, 추천은 제외 (기획 §F3-4)")
                .containsExactly(freeOther, unknownOther);
        for (JsonNode edition : others) {
            assertThat(edition.path("hasFile").asBoolean())
                    .as("파일 없는 판본은 한 줄도 나오지 않는다 — 그게 우리가 없애기로 한 IMSLP 의 화면이다")
                    .isTrue();
        }

        assertThat(data.path("imslpOnlyCount").asInt())
                .as("'IMSLP 에는 이 곡의 다른 악보가 N개 더 있어요' 의 N")
                .isEqualTo(FILELESS_CREATED);
        assertThat(data.path("downloadableOtherCount").asInt())
                .as("파일+FREE 인 다른 판본 수 — 목록과 무관하게 전체 기준")
                .isEqualTo(1);
        assertThat(data.has("otherEditionsTotal"))
                .as("""
                        삭제한 필드다. 새 규칙에서는 목록이 잘리지 않아 언제나 otherEditions.length 와 같다 —
                        남겨 두면 "잘린 개수" 라는 없어진 개념을 화면이 다시 계산하게 된다.""")
                .isFalse();
    }

    @Test
    @DisplayName("파일 없는 판본이 하나도 없으면 imslpOnlyCount 는 0 — 화면은 안내 줄을 만들지 않는다")
    void noFilelessEditionMeansNoGuideLine() throws Exception {
        Tokens admin = loginAdmin();
        long workId = createWork(admin, createComposer(admin));

        long recommended = createFileEdition(admin, workId, "FREE", "추천 판정 근거");
        setRecommended(admin, workId, recommended);
        long other = createFileEdition(admin, workId, "FREE", "다른 판본 판정 근거");

        JsonNode data = data(mockMvc.perform(get("/api/works/{id}", workId)).andExpect(status().isOk()));

        assertThat(longs(data.path("otherEditions"), "id")).containsExactly(other);
        assertThat(data.has("imslpOnlyCount"))
                .as("0 이어도 키는 있어야 한다(§0-4) — 없는 키를 0 으로 읽으면 '없다'와 '0개'가 구분되지 않는다")
                .isTrue();
        assertThat(data.path("imslpOnlyCount").asInt()).isZero();
        assertThat(data.path("downloadableOtherCount").asInt()).isEqualTo(1);
    }

    @Test
    @DisplayName("준비 중 곡(파일 있는 판본 0개): otherEditions 는 비고 imslpOnlyCount 만 남는다")
    void preparingWorkHasOnlyTheCount() throws Exception {
        Tokens admin = loginAdmin();
        long workId = createWork(admin, createComposer(admin));
        createInfoEdition(admin, workId);
        createInfoEdition(admin, workId);

        JsonNode data = data(mockMvc.perform(get("/api/works/{id}", workId)).andExpect(status().isOk()));

        assertThat(data.path("recommendedEdition").isNull()).isTrue();
        assertThat(data.path("otherEditions")).isEmpty();
        assertThat(data.path("imslpOnlyCount").asInt()).isEqualTo(2);
        assertThat(data.path("downloadableOtherCount").asInt()).isZero();
    }
}
