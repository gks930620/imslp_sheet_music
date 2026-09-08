package com.test.test.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.test.test.integration.support.AdminApiTestSupport;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 곡 상세의 "다른 판본" 은 1차 범위(피아노 독주) 안의 것만 안내한다 —
 * 02_API_명세서 §3-3 (2026-09-08 개정, qa 3차 결함 5).
 *
 * <p><b>실측이 가정을 넘겼다.</b> §3-3 상한을 정할 때 쓴 수치는 "곡당 판본 70개" 였는데
 * qa 3차 실측은 <b>최대 207개(평균 79.8개)</b> 였고, 그중 <b>73~83%가 {@code ARRANGEMENT}(편곡)</b> 였다.
 * 기획 §0-2 가 1차 범위를 <b>피아노 독주곡</b>으로 못 박았는데, 곡 상세의 "다른 판본" 대부분이
 * 기타·성악·2대 피아노 편곡 스캔이라는 뜻이다.
 *
 * <p><b>확정 계약</b> — 파일 없는 판본을 담는 5칸은 {@code kind = COMPLETE_SCORE} 만 받는다.
 * <ul>
 *   <li>이 목록의 유일한 용도는 <b>"IMSLP 에 가면 더 있다"</b> 는 안내다. 그 안내가 범위 밖 편성으로 채워지면
 *       안내로서 <b>틀린 정보</b>이고, "판본 고민 없이 1개" 라는 제품 약속과도 어긋난다.
 *       {@code PARTS}(파트보)도 같다 — 파트보가 있다는 건 애초에 앙상블 곡이라는 뜻이다.</li>
 *   <li><b>파일이 있는 판본은 kind 와 무관하게 전부 남긴다.</b> 우리가 실제로 줄 수 있는 것이고,
 *       편곡에 파일이 붙어 있다면 관리자가 의도해 붙인 것이다(수집 자동 지정은 COMPLETE_SCORE 만 고른다).</li>
 *   <li>{@code otherEditionsTotal} 도 <b>같은 모집단</b>으로 센다. 화면은 {@code total - length} 로
 *       "… 외 N개는 IMSLP에서" 를 만든다(§3-3) — 모집단이 다르면 그 뺄셈이 뜻을 잃는다.</li>
 *   <li><b>관리 화면(§4-7)은 그대로 전부 보여 준다.</b> 관리자는 편곡도 판정·추천 후보 판단을 해야 한다.</li>
 * </ul>
 */
class WorkDetailEditionKindFilterIntegrationTest extends AdminApiTestSupport {

    @Test
    @DisplayName("파일 없는 편곡·파트보는 공개 곡 상세에서 빠진다 — 파일 있는 편곡은 남는다")
    void filelessArrangementsAndPartsAreHiddenFromPublicDetail() throws Exception {
        Tokens admin = loginAdmin();
        long composerId = createComposer(admin);
        long workId = createWork(admin, composerId);

        long recommended = createFileEdition(admin, workId, "FREE", "추천 판정 근거");
        setRecommended(admin, workId, recommended);

        long fileArrangement = createEditionOfKind(admin, workId, "ARRANGEMENT", true, "FREE");
        long infoComplete = createInfoEdition(admin, workId);
        long infoArrangement = createEditionOfKind(admin, workId, "ARRANGEMENT", false, "UNKNOWN");
        long infoParts = createEditionOfKind(admin, workId, "PARTS", false, "UNKNOWN");

        JsonNode data = data(mockMvc.perform(get("/api/works/{id}", workId)).andExpect(status().isOk()));

        assertThat(longs(data.path("otherEditions"), "id"))
                .as("파일 있는 판본(편곡 포함) 먼저, 그 뒤에 파일 없는 COMPLETE_SCORE")
                .containsExactly(fileArrangement, infoComplete)
                .doesNotContain(infoArrangement, infoParts, recommended);
        assertThat(data.path("otherEditionsTotal").asInt())
                .as("total 은 목록과 같은 모집단이어야 total - length 가 '잘린 개수'가 된다")
                .isEqualTo(2);
        assertThat(data.path("downloadableOtherCount").asInt())
                .as("파일+FREE 인 다른 판본 — 편곡이어도 받을 수 있으면 센다")
                .isEqualTo(1);

        // 관리 화면은 그대로 전부 본다 (§4-7)
        assertThat(longs(getWork(admin, workId).path("editions"), "id"))
                .as("관리자는 편곡·파트보도 판정·추천 후보 판단을 해야 한다")
                .contains(recommended, fileArrangement, infoComplete, infoArrangement, infoParts);
    }

    @Test
    @DisplayName("편곡이 파일 없는 5칸을 차지하지 않는다 — 편곡 20개가 있어도 COMPLETE_SCORE 3개가 다 보인다")
    void arrangementsDoNotConsumeTheFilelessSlots() throws Exception {
        Tokens admin = loginAdmin();
        long composerId = createComposer(admin);
        long workId = createWork(admin, composerId);

        long recommended = createFileEdition(admin, workId, "FREE", "추천 판정 근거");
        setRecommended(admin, workId, recommended);

        List<Long> complete = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            complete.add(createInfoEdition(admin, workId));
        }
        for (int i = 0; i < 20; i++) {
            createEditionOfKind(admin, workId, "ARRANGEMENT", false, "UNKNOWN");
        }

        JsonNode data = data(mockMvc.perform(get("/api/works/{id}", workId)).andExpect(status().isOk()));

        assertThat(longs(data.path("otherEditions"), "id")).containsExactlyElementsOf(complete);
        assertThat(data.path("otherEditionsTotal").asInt())
                .as("편곡 20개는 모집단에서 빠지므로 '… 외 N개' 도 뜨지 않는다")
                .isEqualTo(3);
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
