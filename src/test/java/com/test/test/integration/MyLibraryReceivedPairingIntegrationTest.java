package com.test.test.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.test.test.integration.support.MemberApiTestSupport;
import com.test.test.sheetmusic.work.WorkDtoAssembler;
import com.test.test.sheetmusic.work.dto.WorkSummaryDTO;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.SpyBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 받은 악보 줄의 <b>곡 카드 ↔ 판본 짝짓기</b> (02 §10-3) — 리뷰 제안 1.
 *
 * <p>목록 조립은 "줄(다운로드 행)" 과 "곡 카드" 를 따로 만들어 붙인다. 이 둘을 <b>순서(index)</b> 로 붙이면
 * 곡 카드를 만드는 쪽이 나중에 한 건이라도 거르는 순간 <b>예외도 없이</b> 곡과 판본이 엇갈린다 —
 * 화면에는 A 곡 제목 아래 B 곡의 판본·크기·다시받기 주소가 걸린다. 그 불변식(1:1·순서보존)은 어디에도
 * 쓰여 있지 않으므로 코드가 순서에 기대면 안 된다.
 *
 * <p>그래서 조립기가 카드를 한 건 거르는 상황을 만들어 두고, 응답이 <b>엉뚱한 짝</b>을 내보내지 않는지 본다.
 * 짝을 못 찾은 줄은 목록에서 빠진다(잘못된 데이터를 내보내느니 그 줄을 말하지 않는다).
 */
class MyLibraryReceivedPairingIntegrationTest extends MemberApiTestSupport {

    @SpyBean
    private WorkDtoAssembler workDtoAssembler;

    @Test
    @DisplayName("곡 카드가 한 건 빠져도 남은 줄의 곡·판본은 엇갈리지 않는다 — 짝 없는 줄은 빠진다")
    void receivedRowsPairByWorkId_notByIndex() throws Exception {
        Tokens admin = loginAdmin();
        Tokens member = loginMember();
        long composerId = createComposer(admin);
        long firstWork = createWork(admin, composerId);
        long secondWork = createWork(admin, composerId);
        long firstEdition = makeReady(admin, firstWork);
        long secondEdition = makeReady(admin, secondWork);

        downloadAs(member, firstEdition).andExpect(status().isOk());
        downloadAs(member, secondEdition).andExpect(status().isOk());

        // 줄은 [나중 곡, 먼저 곡] 인데 카드는 '먼저 곡' 하나뿐인 상황 — 순서로 붙이면 첫 줄이 엇갈린다
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            List<WorkSummaryDTO> cards = (List<WorkSummaryDTO>) invocation.callRealMethod();
            return cards.stream().filter(card -> card.getId().equals(firstWork)).toList();
        }).when(workDtoAssembler).toSummaries(anyList());

        JsonNode content = downloadTab(member).path("items").path("content");

        assertThat(content.size())
                .as("짝지을 카드가 없는 줄은 뺀다 — 자리를 채우려고 남의 카드를 끌어다 쓰지 않는다")
                .isEqualTo(1);
        assertThat(content.get(0).path("work").path("id").asLong()).isEqualTo(firstWork);
        assertThat(content.get(0).path("receivedEdition").path("id").asLong())
                .as("남은 줄의 판본은 그 곡에서 받은 판본이어야 한다(%d 가 아니라 %d)", secondEdition, firstEdition)
                .isEqualTo(firstEdition);
    }
}
