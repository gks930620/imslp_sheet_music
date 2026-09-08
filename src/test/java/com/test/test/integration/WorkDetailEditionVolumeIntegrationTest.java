package com.test.test.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.test.test.integration.support.AdminApiTestSupport;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 곡 상세의 "다른 판본" 분량 계약 — 02_API_명세서 §3-3 (2026-09-07 개정) / 화면정의 03 §3-3.
 *
 * <p><b>왜 계약을 바꾸는가.</b> 설계는 "곡당 판본 몇 개"를 전제로 {@code otherEditions} 를 <b>전량</b> 내려보냈다.
 * 실제 IMSLP 수집 결과는 <b>곡 1개당 판본 70개</b>(20곡 1,792개)이고, 그중 우리가 파일을 받은 것은 곡당 최대 2개다
 * (01 §9-1). 전량을 내려보내면 (1) 곡 상세 응답이 수십 KB 로 부풀고, (2) 사용자 화면에는
 * "파일 없음 · IMSLP에서 보기" 행이 68줄 깔려 <b>"판본 고민 없이 1개"</b> 라는 제품 약속과 정면으로 어긋난다.
 *
 * <p><b>바뀐 계약</b> (모두 이 테스트가 지킨다)
 * <ul>
 *   <li>{@code otherEditions} = 추천을 뺀 판본 중 <b>파일 있는 것 전부</b>(개수는 우리가 통제한다)
 *       <b>+ 파일 없는 것 최대 5개</b>. 각 구간 안 정렬은 기존과 같다(IMSLP 다운로드 수 DESC NULLS LAST, id ASC).</li>
 *   <li>{@code otherEditionsTotal}(신규) = 추천을 뺀 <b>전체</b> 판본 수. 잘렸는지 화면이 알 수 있어야 한다.</li>
 *   <li>{@code downloadableOtherCount} 는 <b>내려보낸 목록이 아니라 전체</b> 기준을 유지한다 —
 *       "바로 받을 수 있는 다른 판본이 N개 있어요" 안내가 잘림 때문에 틀리면 안 된다.</li>
 * </ul>
 */
class WorkDetailEditionVolumeIntegrationTest extends AdminApiTestSupport {

    /** 파일 없는 판본을 목록에 몇 개까지 실어 보내는가 (02 §3-3). */
    private static final int FILELESS_LIMIT = 5;
    private static final int FILELESS_CREATED = 8;

    @Test
    @DisplayName("판본 11개(파일 3 + 정보만 8): otherEditions 는 파일 있는 2 + 파일 없는 5, total 10, downloadableOtherCount 는 전체 기준")
    void otherEditions_areCapped_butCountsStayWhole() throws Exception {
        Tokens admin = loginAdmin();
        long composerId = createComposer(admin);
        long workId = createWork(admin, composerId);

        long recommended = createFileEdition(admin, workId, "FREE", "추천 판정 근거");
        setRecommended(admin, workId, recommended);
        long freeOther = createFileEdition(admin, workId, "FREE", "다른 판본 판정 근거");
        long unknownOther = createFileEdition(admin, workId, "UNKNOWN", null);
        List<Long> fileless = new ArrayList<>();
        for (int i = 0; i < FILELESS_CREATED; i++) {
            fileless.add(createInfoEdition(admin, workId));
        }

        JsonNode data = data(mockMvc.perform(get("/api/works/{id}", workId)).andExpect(status().isOk()));

        assertThat(data.path("status").asText()).isEqualTo("READY");
        assertThat(data.path("recommendedEdition").path("id").asLong()).isEqualTo(recommended);

        // 전체 개수는 잘림과 무관하게 그대로 보인다
        assertThat(data.path("otherEditionsTotal").asInt())
                .as("추천을 뺀 전체 판본 수(파일 유무 무관)")
                .isEqualTo(2 + FILELESS_CREATED);
        assertThat(data.path("downloadableOtherCount").asInt())
                .as("파일+FREE 인 다른 판본 수 — 목록이 잘려도 전체 기준")
                .isEqualTo(1);

        JsonNode others = data.path("otherEditions");
        assertThat(others.size()).isEqualTo(2 + FILELESS_LIMIT);

        // 파일 있는 판본이 앞에 전부, 그 뒤에 파일 없는 판본 5개
        assertThat(longs(others, "id").subList(0, 2)).containsExactly(freeOther, unknownOther);
        assertThat(longs(others, "id").subList(2, others.size()))
                .containsExactlyElementsOf(fileless.subList(0, FILELESS_LIMIT));
        for (int i = 0; i < others.size(); i++) {
            assertThat(others.get(i).path("hasFile").asBoolean())
                    .as("%d번째 항목의 hasFile", i)
                    .isEqualTo(i < 2);
        }
        assertThat(longs(others, "id")).doesNotContain(recommended);
    }

    @Test
    @DisplayName("판본이 적은 곡은 그대로 전부 내려간다 — 상한은 잘라야 할 때만 작동한다")
    void smallWork_isUnchanged() throws Exception {
        Tokens admin = loginAdmin();
        long composerId = createComposer(admin);
        long workId = createWork(admin, composerId);

        long recommended = createFileEdition(admin, workId, "FREE", "추천 판정 근거");
        setRecommended(admin, workId, recommended);
        long infoA = createInfoEdition(admin, workId);
        long infoB = createInfoEdition(admin, workId);

        JsonNode data = data(mockMvc.perform(get("/api/works/{id}", workId)).andExpect(status().isOk()));

        assertThat(data.path("otherEditionsTotal").asInt()).isEqualTo(2);
        assertThat(longs(data.path("otherEditions"), "id")).containsExactly(infoA, infoB);
        assertThat(data.path("downloadableOtherCount").asInt()).isZero();
    }
}
