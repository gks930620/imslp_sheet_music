package com.test.test.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.test.test.integration.support.MemberApiTestSupport;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 받은 악보 — 02 §10-3 · §3-4(로그인 주체 기록) · 01_ERD §3-12.
 * 기획 05 §3, 화면정의 09 §1-2·§1-2-2(다시 받기 3상태), 인수 조건 8-D 1~12 · 8-C 9.
 *
 * <p><b>회귀가 이 파일의 절반이다.</b> "누가" 를 붙이면서 기존 집계가 흔들리면 안 된다 —
 * 비로그인 다운로드는 계속 관리 홈 "이번 달 다운로드" 에 들고(8-D 3), {@code HEAD} 는 여전히 아무것도
 * 기록하지 않으며(§3-4), 다운로드 사용감은 클릭 1번 그대로다(확인 창·로그인 요구 없음 — 기획 05 §3-1).
 */
class MyLibraryDownloadApiIntegrationTest extends MemberApiTestSupport {

    /** {@code created_at} 은 어느 응답에도 실리지 않는다(01_ERD §3-12) — 그래서 표를 직접 본다. */
    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** 같은 트랜잭션이라 JPA 조회(=자동 flush) 뒤에 읽는다. */
    private Instant timestampOf(long workId, String column) {
        Timestamp value = jdbcTemplate.queryForObject(
                "select " + column + " from user_work_download where work_id = ?", Timestamp.class, workId);
        return value == null ? null : value.toInstant();
    }

    // ==================== 무엇이 기록되나 (기획 05 §3-1) ====================

    @Nested
    @DisplayName("§3-1 무엇이 남나")
    class WhatIsRecorded {

        @Test
        @DisplayName("로그인 상태로 받으면 받은 악보에 한 줄 — 곡 카드 + 받은 날짜 + 받은 판본 (8-D 1)")
        void loggedInDownloadBecomesARow() throws Exception {
            Tokens admin = loginAdmin();
            Tokens member = loginMember();
            long workId = createWork(admin, createComposer(admin));
            long editionId = makeReady(admin, workId);

            downloadAs(member, editionId).andExpect(status().isOk());

            JsonNode row = downloadRow(member, workId);
            assertThat(row).as("받은 곡이 목록에 있어야 한다").isNotNull();
            assertThat(row.path("work").path("id").asLong()).isEqualTo(workId);
            assertThat(row.path("downloadedAt").asText()).isNotBlank();
            assertThat(row.path("receivedEdition").path("id").asLong()).isEqualTo(editionId);
            assertThat(row.path("receivedEdition").path("kind").asText()).isEqualTo("COMPLETE_SCORE");
            assertThat(row.path("receivedEdition").path("scope").asText()).isEqualTo("COMPLETE");
            assertThat(row.path("receivedEdition").path("pageCount").isNull()).isFalse();
            assertThat(row.path("receivedEdition").path("fileSize").asLong()).isPositive();
        }

        @Test
        @DisplayName("처음 받은 시각은 그 다운로드 시각이다 — 넣는 문장이 따로 계산하지 않는다 (01_ERD §3-12 · 03 §26-2)")
        void createdAtIsTheDownloadTime() throws Exception {
            Tokens admin = loginAdmin();
            Tokens member = loginMember();
            long workId = createWork(admin, createComposer(admin));
            long editionId = makeReady(admin, workId);
            Instant beforeDownload = Instant.now().minusSeconds(60);

            downloadAs(member, editionId).andExpect(status().isOk());
            downloadRow(member, workId);

            Instant createdAt = timestampOf(workId, "created_at");
            assertThat(createdAt)
                    .as("'처음 받은 시각' 과 '마지막으로 받은 시각' 은 첫 줄에서 같은 값이다 — 규칙이 두 곳에 생기면 여기서 갈린다")
                    .isEqualTo(timestampOf(workId, "last_downloaded_at"));
            assertThat(createdAt)
                    .as("창이 ±60초로 넓은 것은 의도다 — 시계가 아니라 엉뚱한 값(0, 시간대가 밀린 값)을 잡는다")
                    .isBetween(beforeDownload, Instant.now().plusSeconds(60));
        }

        @Test
        @DisplayName("비로그인으로 받은 것은 남지 않는다 — 그래도 이번 달 다운로드에는 든다 (8-D 3 회귀)")
        void anonymousDownloadCountsButIsNotMine() throws Exception {
            Tokens admin = loginAdmin();
            Tokens member = loginMember();
            long workId = createWork(admin, createComposer(admin));
            long editionId = makeReady(admin, workId);
            long before = monthlyDownloads(admin);

            downloadAs(null, editionId).andExpect(status().isOk());

            assertThat(monthlyDownloads(admin))
                    .as("비로그인 다운로드도 집계에는 그대로 든다 — 이 숫자가 줄면 회귀다")
                    .isEqualTo(before + 1);
            assertThat(downloadWorkIds(member))
                    .as("누구의 것도 아니다. 나중에 로그인해도 붙지 않는다(기획 05 §3-1)")
                    .isEmpty();
            assertThat(downloadTab(member).path("counts").path("downloads").asInt()).isZero();
        }

        @Test
        @DisplayName("HEAD 는 아무것도 기록하지 않는다 — 사전 확인이 선반에 줄을 만들지 않는다 (§3-4)")
        void headRecordsNothing() throws Exception {
            Tokens admin = loginAdmin();
            Tokens member = loginMember();
            long workId = createWork(admin, createComposer(admin));
            long editionId = makeReady(admin, workId);
            long before = monthlyDownloads(admin);

            headDownloadAs(member, editionId).andExpect(status().isOk());

            assertThat(monthlyDownloads(admin)).isEqualTo(before);
            assertThat(downloadWorkIds(member)).isEmpty();
        }

        @Test
        @DisplayName("같은 곡을 세 번 받아도 한 줄이고 날짜는 가장 최근 것 (8-D 5)")
        void sameWorkStaysOneRow() throws Exception {
            Tokens admin = loginAdmin();
            Tokens member = loginMember();
            long workId = createWork(admin, createComposer(admin));
            long editionId = makeReady(admin, workId);

            downloadAs(member, editionId).andExpect(status().isOk());
            String firstAt = downloadRow(member, workId).path("downloadedAt").asText();
            downloadAs(member, editionId).andExpect(status().isOk());
            downloadAs(member, editionId).andExpect(status().isOk());

            assertThat(downloadWorkIds(member)).containsExactly(workId);
            assertThat(downloadTab(member).path("counts").path("downloads").asInt()).isEqualTo(1);
            assertThat(downloadRow(member, workId).path("downloadedAt").asText())
                    .as("받은 날짜는 갱신된다 — 선반의 날짜는 '마지막으로 받은 날' 이다")
                    .isGreaterThanOrEqualTo(firstAt);
        }

        @Test
        @DisplayName("최근에 받은 순 — 나중에 받은 곡이 맨 위 (기획 05 §3-2)")
        void sortedByMostRecentDownload() throws Exception {
            Tokens admin = loginAdmin();
            Tokens member = loginMember();
            long composerId = createComposer(admin);
            long firstWork = createWork(admin, composerId);
            long secondWork = createWork(admin, composerId);
            long firstEdition = makeReady(admin, firstWork);
            long secondEdition = makeReady(admin, secondWork);

            downloadAs(member, firstEdition).andExpect(status().isOk());
            downloadAs(member, secondEdition).andExpect(status().isOk());

            assertThat(downloadWorkIds(member)).containsExactly(secondWork, firstWork);
        }

        @Test
        @DisplayName("같은 곡의 다른 판본을 받으면 '마지막 판본' 이 그것으로 바뀐다 (8-D 2)")
        void lastEditionFollowsTheLastDownload() throws Exception {
            Tokens admin = loginAdmin();
            Tokens member = loginMember();
            long workId = createWork(admin, createComposer(admin));
            long recommended = makeReady(admin, workId);
            long other = createFileEdition(admin, workId, "FREE", "다른 판본 판정 근거");

            downloadAs(member, recommended).andExpect(status().isOk());
            downloadAs(member, other).andExpect(status().isOk());

            JsonNode row = downloadRow(member, workId);
            assertThat(row.path("receivedEdition").path("id").asLong()).isEqualTo(other);
            assertThat(row.path("redownloadState").asText())
                    .as("마지막에 받은 것이 지금 추천과 다르면 ②다")
                    .isEqualTo("RECOMMENDATION_CHANGED");
        }

        @Test
        @DisplayName("다른 계정이 받은 것은 보이지 않는다 (8-C 9)")
        void otherMembersDownloadsAreInvisible() throws Exception {
            Tokens admin = loginAdmin();
            Tokens member = loginMember();
            Tokens other = loginOtherMember();
            long composerId = createComposer(admin);
            long mine = createWork(admin, composerId);
            long theirs = createWork(admin, composerId);

            downloadAs(member, makeReady(admin, mine)).andExpect(status().isOk());
            downloadAs(other, makeReady(admin, theirs)).andExpect(status().isOk());

            assertThat(downloadWorkIds(member)).containsExactly(mine);
            assertThat(downloadWorkIds(other)).containsExactly(theirs);
        }

        @Test
        @DisplayName("숨긴 곡은 목록·숫자에서 빠지고 숨김을 풀면 돌아온다 (8-D 10)")
        void hiddenWorkDisappearsAndComesBack() throws Exception {
            Tokens admin = loginAdmin();
            Tokens member = loginMember();
            long workId = createWork(admin, createComposer(admin));
            downloadAs(member, makeReady(admin, workId)).andExpect(status().isOk());

            setWorkHidden(admin, workId, true);
            assertThat(downloadWorkIds(member)).isEmpty();
            assertThat(downloadTab(member).path("counts").path("downloads").asInt()).isZero();

            setWorkHidden(admin, workId, false);
            assertThat(downloadWorkIds(member)).containsExactly(workId);
            assertThat(downloadTab(member).path("counts").path("downloads").asInt()).isEqualTo(1);
        }

        @Test
        @DisplayName("받은 악보를 지우는 문은 없다 (8-D 9) — 1차에 삭제 API 를 만들지 않는다")
        void noDeleteEndpoint() throws Exception {
            Tokens member = loginMember();

            mockMvc.perform(delete("/api/me/library/downloads")
                            .header("Authorization", bearer(member.accessToken())))
                    .andExpect(status().is4xxClientError());
        }
    }

    // ==================== 다시 받기 3상태 (화면정의 09 §1-2-2) ====================

    @Nested
    @DisplayName("§10-3 다시 받기 3상태")
    class RedownloadStates {

        @Test
        @DisplayName("① 그때 판본이 지금 추천 그대로 — AVAILABLE, 다시 받기는 그 판본")
        void available() throws Exception {
            Tokens admin = loginAdmin();
            Tokens member = loginMember();
            long workId = createWork(admin, createComposer(admin));
            long editionId = makeReady(admin, workId);
            downloadAs(member, editionId).andExpect(status().isOk());

            JsonNode row = downloadRow(member, workId);
            assertThat(row.path("redownloadState").asText()).isEqualTo("AVAILABLE");
            assertThat(row.path("redownloadUrl").asText()).isEqualTo("/api/editions/" + editionId + "/download");
            assertThat(row.path("alternativeEdition").isNull())
                    .as("① 에는 대체가 없다 — 화면에 버튼은 하나뿐이다")
                    .isTrue();
        }

        @Test
        @DisplayName("② 받을 수는 있는데 지금 추천이 다름 — RECOMMENDATION_CHANGED, 다시 받기는 여전히 그때 판본 (8-D 7)")
        void recommendationChanged() throws Exception {
            Tokens admin = loginAdmin();
            Tokens member = loginMember();
            long workId = createWork(admin, createComposer(admin));
            long received = makeReady(admin, workId);
            downloadAs(member, received).andExpect(status().isOk());

            long newRecommended = createFileEdition(admin, workId, "FREE", "새 추천 판정 근거");
            setRecommended(admin, workId, newRecommended);

            JsonNode row = downloadRow(member, workId);
            assertThat(row.path("redownloadState").asText()).isEqualTo("RECOMMENDATION_CHANGED");
            assertThat(row.path("redownloadUrl").asText())
                    .as("바꿔치기하지 않는다 — 사용자가 원하는 건 연습하던 그 악보다(기획 05 §3-3)")
                    .isEqualTo("/api/editions/" + received + "/download");
            assertThat(row.path("receivedEdition").path("id").asLong()).isEqualTo(received);
        }

        @Test
        @DisplayName("③ 그때 판본이 '이용 제한' 으로 바뀜 — UNAVAILABLE, 그 판본 주소는 어디에도 없다 (8-D 8)")
        void unavailableWhenRestricted() throws Exception {
            Tokens admin = loginAdmin();
            Tokens member = loginMember();
            long workId = createWork(admin, createComposer(admin));
            long received = makeReady(admin, workId);
            downloadAs(member, received).andExpect(status().isOk());

            adminPut(admin, "/api/admin/editions/{id}/copyright",
                    json("koreaCopyright", "RESTRICTED", "copyrightNote", "판정 번복"), received)
                    .andExpect(status().isOk());

            JsonNode row = downloadRow(member, workId);
            assertThat(row.path("redownloadState").asText()).isEqualTo("UNAVAILABLE");
            assertThat(row.toString())
                    .as("이용 제한 판본의 주소는 응답 어디에도 실리지 않는다 — 곡 상세가 못 주는 것을 받은 악보가 주면 구멍이다")
                    .doesNotContain("/api/editions/" + received + "/download");
            assertThat(row.path("receivedEdition").path("id").asLong())
                    .as("무엇을 못 받는지는 계속 보인다(받은 판본 줄)")
                    .isEqualTo(received);

            downloadAs(member, received)
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.errorCode").value("COPYRIGHT_RESTRICTED"));
        }

        @Test
        @DisplayName("③-a 지금 추천을 바로 받을 수 있으면 대체를 준다 — alternativeEdition + 추천 판본 주소")
        void unavailableWithAlternative() throws Exception {
            Tokens admin = loginAdmin();
            Tokens member = loginMember();
            long workId = createWork(admin, createComposer(admin));
            long received = makeReady(admin, workId);
            downloadAs(member, received).andExpect(status().isOk());

            long newRecommended = createFileEdition(admin, workId, "FREE", "새 추천 판정 근거");
            setRecommended(admin, workId, newRecommended);
            adminDelete(admin, "/api/admin/editions/{id}", received).andExpect(status().isNoContent());

            JsonNode row = downloadRow(member, workId);
            assertThat(row.path("redownloadState").asText()).isEqualTo("UNAVAILABLE");
            assertThat(row.path("redownloadUrl").asText())
                    .as("③-a 의 버튼은 '지금 추천 판본 받기' 다")
                    .isEqualTo("/api/editions/" + newRecommended + "/download");
            assertThat(row.path("alternativeEdition").path("id").asLong()).isEqualTo(newRecommended);
            assertThat(row.path("receivedEdition").path("id").isNull())
                    .as("삭제된 판본은 id 가 없다 — 설명만 스냅샷으로 남는다(01_ERD §3-12)")
                    .isTrue();
            assertThat(row.path("receivedEdition").path("kind").asText())
                    .as("무엇을 못 주는지 말할 수 있어야 '지금 추천 판본 받기' 가 대체로 읽힌다(09 §6 S4)")
                    .isEqualTo("COMPLETE_SCORE");
            assertThat(row.path("receivedEdition").path("fileSize").asLong()).isPositive();
        }

        @Test
        @DisplayName("③-b 지금 추천도 못 주면 버튼이 없다 — 주소 둘 다 null (화면은 '곡 보기')")
        void unavailableWithoutAlternative() throws Exception {
            Tokens admin = loginAdmin();
            Tokens member = loginMember();
            long workId = createWork(admin, createComposer(admin));
            long received = makeReady(admin, workId);
            downloadAs(member, received).andExpect(status().isOk());

            adminDelete(admin, "/api/admin/editions/{id}", received).andExpect(status().isNoContent());

            JsonNode row = downloadRow(member, workId);
            assertThat(row.path("redownloadState").asText()).isEqualTo("UNAVAILABLE");
            assertThat(row.path("redownloadUrl").isNull()).isTrue();
            assertThat(row.path("alternativeEdition").isNull()).isTrue();
            assertThat(row.path("work").path("status").asText())
                    .as("추천이 사라졌으니 곡은 준비 중이다 — 목록의 곡 카드는 지금 상태를 보인다")
                    .isEqualTo("PREPARING");
        }

        @Test
        @DisplayName("③ 파일만 사라진 판본도 못 준다 — 판정은 FREE 여도 (8-D 12 의 뿌리)")
        void unavailableWhenFileRemoved() throws Exception {
            Tokens admin = loginAdmin();
            Tokens member = loginMember();
            long workId = createWork(admin, createComposer(admin));
            long received = makeReady(admin, workId);
            downloadAs(member, received).andExpect(status().isOk());

            Map<String, Object> withoutFile = editionSaveBodyFrom(getEdition(admin, received));
            withoutFile.put("fileId", null);
            withoutFile.put("previewFileId", null);
            adminPut(admin, "/api/admin/editions/{id}", withoutFile, received).andExpect(status().isOk());

            JsonNode row = downloadRow(member, workId);
            assertThat(row.path("redownloadState").asText()).isEqualTo("UNAVAILABLE");
            assertThat(row.path("redownloadUrl").isNull()).isTrue();
        }

        @Test
        @DisplayName("받은 판본의 설명은 '지금 값' 이다 — 파일이 바뀌면 바뀐 쪽수를 보인다 (§10-3)")
        void receivedEditionShowsCurrentValuesWhileTheEditionLives() throws Exception {
            Tokens admin = loginAdmin();
            Tokens member = loginMember();
            long workId = createWork(admin, createComposer(admin));
            long received = makeReady(admin, workId);
            downloadAs(member, received).andExpect(status().isOk());

            Map<String, Object> edited = editionSaveBodyFrom(getEdition(admin, received));
            edited.put("pageCount", 77);
            adminPut(admin, "/api/admin/editions/{id}", edited, received).andExpect(status().isOk());

            assertThat(downloadRow(member, workId).path("receivedEdition").path("pageCount").asInt())
                    .as("이 줄은 '누르면 무엇이 오는가' 를 말한다 — 살아 있는 판본이면 지금 값이 옳다")
                    .isEqualTo(77);
        }
    }
}
