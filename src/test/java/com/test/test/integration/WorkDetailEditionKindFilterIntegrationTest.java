package com.test.test.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.test.test.integration.support.AdminApiTestSupport;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * "다른 판본" 과 IMSLP 안내 숫자에서 <b>편성(kind)을 어떻게 다루는가</b> — 02_API_명세서 §3-3.
 *
 * <h2>2026-09-08 두 번째 개정 — 필터가 필요 없어졌다</h2>
 * 앞선 개정(qa 3차 결함 5)은 <b>파일 없는 5칸</b>이 기타·성악·2대 피아노 편곡으로 채워지는 것을 막으려고
 * 그 구간에 {@code kind = COMPLETE_SCORE} 필터를 걸었다. 이번 계약 통일({@link WorkDetailEditionVolumeIntegrationTest})로
 * <b>파일 없는 구간 자체가 사라졌으므로</b> 그 필터가 지킬 대상이 없다. 남은 질문은 하나다 —
 * "IMSLP 에 N개 더 있어요" 의 <b>N 을 편성으로 걸러 세는가.</b>
 *
 * <h2>확정: 숫자는 걸러 세지 않는다</h2>
 * <ul>
 *   <li>이 숫자는 <b>IMSLP 작품 페이지로 보내는 링크</b>의 개수 안내다. 그 페이지에 실제로 있는 것은
 *       편곡·파트보를 포함한 전부다. 걸러 세면 사용자가 링크를 눌러 보는 것과 우리 숫자가 어긋난다 —
 *       <b>걸러야 틀리는 숫자</b>다. (5칸 목록을 걸렀던 이유 "안내가 틀린 정보가 된다" 는, 목록이 없어지면서 함께 사라졌다.
 *       목록은 <b>무엇을 보여줄지</b>의 문제였고 숫자는 <b>거기 몇 개가 있는지</b>의 문제다.)</li>
 *   <li><b>파일 있는 판본은 편성과 무관하게 전부 줄로 남긴다</b> — 우리가 실제로 줄 수 있는 것이고,
 *       편곡에 파일이 붙어 있다면 관리자가 의도해 붙인 것이다(수집 자동 지정은 {@code COMPLETE_SCORE} 만 고른다, 01 §3-3).</li>
 *   <li><b>관리 화면(§4-7)은 그대로 전부 보여 준다</b> — 관리자는 편곡·파트보도 판정과 추천 후보 판단을 해야 한다.</li>
 * </ul>
 */
class WorkDetailEditionKindFilterIntegrationTest extends AdminApiTestSupport {

    @Test
    @DisplayName("파일 있는 편곡은 줄로 남고, 파일 없는 편곡·파트보는 편성을 가리지 않고 imslpOnlyCount 에 센다")
    void arrangementsCountTowardTheImslpGuideNumber() throws Exception {
        Tokens admin = loginAdmin();
        long workId = createWork(admin, createComposer(admin));

        long recommended = createFileEdition(admin, workId, "FREE", "추천 판정 근거");
        setRecommended(admin, workId, recommended);

        long fileArrangement = createEditionOfKind(admin, workId, "ARRANGEMENT", true, "FREE");
        long infoComplete = createInfoEdition(admin, workId);
        long infoArrangement = createEditionOfKind(admin, workId, "ARRANGEMENT", false, "UNKNOWN");
        long infoParts = createEditionOfKind(admin, workId, "PARTS", false, "UNKNOWN");

        JsonNode data = data(mockMvc.perform(get("/api/works/{id}", workId)).andExpect(status().isOk()));

        assertThat(longs(data.path("otherEditions"), "id"))
                .as("파일 있는 판본만 줄이 된다 — 편곡이어도 우리가 줄 수 있으면 보여준다")
                .containsExactly(fileArrangement)
                .doesNotContain(recommended, infoComplete, infoArrangement, infoParts);
        assertThat(data.path("imslpOnlyCount").asInt())
                .as("파일 없는 3개(전체 악보 1 + 편곡 1 + 파트보 1) 전부를 센다 — IMSLP 페이지에 실제로 있는 수와 맞아야 한다")
                .isEqualTo(3);
        assertThat(data.path("downloadableOtherCount").asInt())
                .as("파일+FREE 인 다른 판본 — 편곡이어도 받을 수 있으면 센다")
                .isEqualTo(1);

        assertThat(longs(getWork(admin, workId).path("editions"), "id"))
                .as("관리자는 편곡·파트보도 판정·추천 후보 판단을 해야 한다 (§4-7)")
                .contains(recommended, fileArrangement, infoComplete, infoArrangement, infoParts);
    }

    @Test
    @DisplayName("파일 없는 편곡이 20개여도 목록은 그대로 비고 숫자만 20 오른다")
    void manyArrangementsOnlyMoveTheNumber() throws Exception {
        Tokens admin = loginAdmin();
        long workId = createWork(admin, createComposer(admin));

        long recommended = createFileEdition(admin, workId, "FREE", "추천 판정 근거");
        setRecommended(admin, workId, recommended);
        for (int i = 0; i < 20; i++) {
            createEditionOfKind(admin, workId, "ARRANGEMENT", false, "UNKNOWN");
        }

        JsonNode data = data(mockMvc.perform(get("/api/works/{id}", workId)).andExpect(status().isOk()));

        assertThat(data.path("otherEditions"))
                .as("판본 21개짜리 곡에서도 사용자가 보는 줄은 0개다")
                .isEmpty();
        assertThat(data.path("imslpOnlyCount").asInt()).isEqualTo(20);
    }

    private long createEditionOfKind(Tokens admin, long workId, String kind, boolean withFile, String koreaCopyright)
            throws Exception {
        Map<String, Object> body;
        if (withFile) {
            JsonNode upload = uploadSamplePdf(admin);
            body = editionBody(upload.path("fileId").asLong(), upload.path("previewFileId").asLong(),
                    upload.path("pageCount").asInt(), koreaCopyright, "테스트 판정 근거");
        } else {
            body = editionBody(null, null, null, koreaCopyright,
                    "UNKNOWN".equals(koreaCopyright) ? null : "테스트 판정 근거");
        }
        body.put("kind", kind);
        body.put("arranger", "ARRANGEMENT".equals(kind) ? "Test Arranger" : null);
        return createEdition(admin, workId, body);
    }
}
