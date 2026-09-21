package com.test.test.integration.support;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 회원 개인화 API(02 §10) 통합테스트 공통 헬퍼 — 즐겨찾기·내 악보.
 *
 * <p>관리 픽스처(곡·판본·추천·숨김)가 그대로 필요하므로 {@link AdminApiTestSupport} 를 잇는다.
 * 이 클래스가 더하는 것은 <b>일반 회원 두 명</b>(시드 {@code user4}/{@code user5}, 둘 다 USER 역할뿐)과
 * {@code /api/me/**} 요청 형식이다. 계정이 둘인 이유: "다른 사람 것은 어떤 주소로도 볼 수 없다"
 * (기획 05 §3-1 · 인수 조건 8-C 9)를 시험하려면 서로 다른 두 주체가 있어야 한다.
 *
 * <p><b>이 API 에는 사용자 id 를 받는 자리가 없다</b>(02 §10-0) — 그래서 아래 헬퍼에도 userId 인자가 없다.
 * 주체는 토큰뿐이다.
 */
public abstract class MemberApiTestSupport extends AdminApiTestSupport {

    protected static final String MEMBER_USERNAME = "user4";
    protected static final String OTHER_MEMBER_USERNAME = "user5";
    private static final String SEED_PASSWORD = "1234";

    protected Tokens loginMember() throws Exception {
        return login(MEMBER_USERNAME, SEED_PASSWORD);
    }

    /** 같은 제품을 쓰는 다른 사람. 즐겨찾기·받은 악보가 계정 사이로 새지 않는지 볼 때 쓴다. */
    protected Tokens loginOtherMember() throws Exception {
        return login(OTHER_MEMBER_USERNAME, SEED_PASSWORD);
    }

    // ===== §10-1 즐겨찾기 =====

    protected ResultActions favoriteOn(Tokens tokens, long workId) throws Exception {
        return mockMvc.perform(put("/api/me/favorites/{workId}", workId)
                .header(HttpHeaders.AUTHORIZATION, bearer(tokens.accessToken())));
    }

    protected ResultActions favoriteOff(Tokens tokens, long workId) throws Exception {
        return mockMvc.perform(delete("/api/me/favorites/{workId}", workId)
                .header(HttpHeaders.AUTHORIZATION, bearer(tokens.accessToken())));
    }

    /** 켜 두고 결과를 확인까지 한다 — given 단계용. */
    protected void givenFavorite(Tokens tokens, long workId) throws Exception {
        favoriteOn(tokens, workId).andExpect(status().isOk());
    }

    // ===== §10-2 · §10-3 내 악보 =====

    /** {@code memberGet(t, "/api/me/library/favorites", "section", "PIANO")} — data 노드를 돌려준다. */
    protected JsonNode memberData(Tokens tokens, String url, String... keyValues) throws Exception {
        MockHttpServletRequestBuilder builder = get(url).header(HttpHeaders.AUTHORIZATION, bearer(tokens.accessToken()));
        for (int i = 0; i < keyValues.length; i += 2) {
            builder.param(keyValues[i], keyValues[i + 1]);
        }
        return data(mockMvc.perform(builder).andExpect(status().isOk()).andReturn());
    }

    protected JsonNode favoriteTab(Tokens tokens, String... keyValues) throws Exception {
        return memberData(tokens, "/api/me/library/favorites", keyValues);
    }

    protected JsonNode downloadTab(Tokens tokens, String... keyValues) throws Exception {
        return memberData(tokens, "/api/me/library/downloads", keyValues);
    }

    protected List<Long> favoriteWorkIds(Tokens tokens, String... keyValues) throws Exception {
        return ids(favoriteTab(tokens, keyValues).path("works").path("content"), "id");
    }

    /** 받은 악보는 곡 단위 한 줄이라 줄의 정체는 {@code work.id} 다. */
    protected List<Long> downloadWorkIds(Tokens tokens, String... keyValues) throws Exception {
        List<Long> list = new ArrayList<>();
        downloadTab(tokens, keyValues).path("items").path("content")
                .forEach(item -> list.add(item.path("work").path("id").asLong()));
        return list;
    }

    /** 받은 악보에서 그 곡의 줄. 없으면 {@code null}. */
    protected JsonNode downloadRow(Tokens tokens, long workId) throws Exception {
        for (JsonNode item : downloadTab(tokens).path("items").path("content")) {
            if (item.path("work").path("id").asLong() == workId) {
                return item;
            }
        }
        return null;
    }

    private List<Long> ids(JsonNode array, String field) {
        List<Long> list = new ArrayList<>();
        array.forEach(node -> list.add(node.path(field).asLong()));
        return list;
    }

    // ===== §3-4 다운로드 (로그인/비로그인) =====

    /** {@code tokens == null} 이면 비로그인 다운로드 — 집계에는 들고 받은 악보에는 안 남는다(8-D 3). */
    protected ResultActions downloadAs(Tokens tokens, long editionId) throws Exception {
        MockHttpServletRequestBuilder builder = get("/api/editions/{id}/download", editionId);
        if (tokens != null) {
            builder.header(HttpHeaders.AUTHORIZATION, bearer(tokens.accessToken()));
        }
        return mockMvc.perform(builder);
    }

    /** 화면이 받기 전에 보내는 사전 확인(§3-4). 아무것도 기록하지 않는 것이 계약이다. */
    protected ResultActions headDownloadAs(Tokens tokens, long editionId) throws Exception {
        MockHttpServletRequestBuilder builder = head("/api/editions/{id}/download", editionId);
        if (tokens != null) {
            builder.header(HttpHeaders.AUTHORIZATION, bearer(tokens.accessToken()));
        }
        return mockMvc.perform(builder);
    }

    protected long monthlyDownloads(Tokens admin) throws Exception {
        return data(adminGet(admin, "/api/admin/dashboard").andExpect(status().isOk()))
                .path("monthlyDownloads").asLong();
    }
}
