package com.test.test.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.test.test.integration.support.MemberApiTestSupport;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 즐겨찾기 — 02 §10-1(켜기/끄기) · §10-2(내 악보 › 즐겨찾기 탭) · §3-3 {@code favorited}.
 * 기획 05 §1·§2-2, 인수 조건 8-A 1~6 · 8-C 2·3·5·6·7·9 · 8-G 5.
 *
 * <p><b>이 테스트가 지키는 계약의 핵심은 "멱등" 이다.</b> 켜기는 {@code PUT}, 끄기는 {@code DELETE} 이고
 * 몇 번을 불러도 결과가 같다. 토글 하나로 만들면 로그인 복귀 완성(03 §22)이 한 번 더 실행되는 순간
 * 즐겨찾기가 <b>꺼져</b> 인수 조건 8-B 4 가 깨진다 — 계약이 화면의 중복 방지와 겹쳐 같은 것을 지킨다.
 */
class FavoriteApiIntegrationTest extends MemberApiTestSupport {

    @PersistenceContext
    private EntityManager entityManager;

    // ==================== §10-1 켜기 / 끄기 ====================

    @Nested
    @DisplayName("§10-1 켜기·끄기")
    class Toggle {

        @Test
        @DisplayName("PUT 200 + {workId, favorited:true} — 그 곡이 즐겨찾기 탭에 들어온다 (8-A 1)")
        void put_turnsOn() throws Exception {
            Tokens member = loginMember();
            long workId = givenWork();

            favoriteOn(member, workId)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.workId").value(workId))
                    .andExpect(jsonPath("$.data.favorited").value(true));

            assertThat(favoriteWorkIds(member)).containsExactly(workId);
        }

        @Test
        @DisplayName("DELETE 204 — 목록에서 빠진다. 꺼져 있는 것을 또 꺼도 204 (멱등, 8-A 2)")
        void delete_turnsOffAndIsIdempotent() throws Exception {
            Tokens member = loginMember();
            long workId = givenWork();
            givenFavorite(member, workId);

            favoriteOff(member, workId).andExpect(status().isNoContent());
            assertThat(favoriteWorkIds(member)).isEmpty();

            favoriteOff(member, workId)
                    .andExpect(status().isNoContent());
            assertThat(favoriteWorkIds(member)).isEmpty();
        }

        @Test
        @DisplayName("이미 켜진 곡을 또 PUT 해도 200 이고 목록 순서가 바뀌지 않는다 — created_at 을 갱신하지 않는다")
        void put_isIdempotent_andKeepsOrder() throws Exception {
            Tokens member = loginMember();
            long first = givenWork();
            long second = givenWork();
            givenFavorite(member, first);
            givenFavorite(member, second);
            assertThat(favoriteWorkIds(member)).containsExactly(second, first);

            favoriteOn(member, first)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.favorited").value(true));

            assertThat(favoriteWorkIds(member))
                    .as("다시 누른 것은 '새로 넣은 것' 이 아니다 — 순서가 흔들리면 '최근에 넣은 순' 이 거짓말이 된다")
                    .containsExactly(second, first);
            assertThat(favoriteTab(member).path("counts").path("favorites").asInt())
                    .as("행이 두 개 생기지 않는다(uk_work_favorite)")
                    .isEqualTo(2);
        }

        @Test
        @DisplayName("비로그인은 401 NOT_AUTHENTICATED — /api/me/** 는 permitAll 에 넣지 않는다 (§10-0)")
        void anonymous_is401() throws Exception {
            long workId = givenWork();

            mockMvc.perform(put("/api/me/favorites/{id}", workId))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.errorCode").value("NOT_AUTHENTICATED"));
            mockMvc.perform(delete("/api/me/favorites/{id}", workId))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.errorCode").value("NOT_AUTHENTICATED"));
            mockMvc.perform(get("/api/me/library/favorites"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.errorCode").value("NOT_AUTHENTICATED"));
            mockMvc.perform(get("/api/me/library/downloads"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.errorCode").value("NOT_AUTHENTICATED"));
        }

        @Test
        @DisplayName("없는 곡·숨김 곡은 404 — 숨긴 곡은 사용자 화면 어디에도 없다 (기획 §F2-6)")
        void missingOrHiddenWork_is404() throws Exception {
            Tokens admin = loginAdmin();
            Tokens member = loginMember();
            long hidden = givenWork();
            setWorkHidden(admin, hidden, true);

            favoriteOn(member, 99_999_999L)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.errorCode").value("NOT_FOUND"));
            favoriteOn(member, hidden)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.errorCode").value("NOT_FOUND"));
            favoriteOff(member, hidden)
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("준비 중 곡도 켜진다 — 다운로드 버튼이 없어도 즐겨찾기는 된다 (8-A 4)")
        void preparingWork_canBeFavorited() throws Exception {
            Tokens member = loginMember();
            long preparing = givenWork(); // 추천 판본이 없는 곡 = PREPARING

            favoriteOn(member, preparing).andExpect(status().isOk());

            JsonNode work = favoriteTab(member).path("works").path("content").get(0);
            assertThat(work.path("id").asLong()).isEqualTo(preparing);
            assertThat(work.path("status").asText())
                    .as("목록은 지금의 상태 뱃지를 보인다 — 열리면 그 자리에서 뱃지가 사라진다(기획 05 §1-4)")
                    .isEqualTo("PREPARING");
        }

        @Test
        @DisplayName("즐겨찾기를 켜고 꺼도 다운로드 수는 오르지 않는다 (8-A 6)")
        void favorite_doesNotCountAsDownload() throws Exception {
            Tokens admin = loginAdmin();
            Tokens member = loginMember();
            long workId = givenWork();
            long before = monthlyDownloads(admin);

            favoriteOn(member, workId).andExpect(status().isOk());
            favoriteOff(member, workId).andExpect(status().isNoContent());

            assertThat(monthlyDownloads(admin)).isEqualTo(before);
        }
    }

    // ==================== §10-2 즐겨찾기 탭 ====================

    @Nested
    @DisplayName("§10-2 즐겨찾기 탭")
    class FavoriteTab {

        @Test
        @DisplayName("최근에 넣은 순 — 맨 위가 방금 넣은 곡 (8-C 3)")
        void sortedByMostRecentlyAdded() throws Exception {
            Tokens member = loginMember();
            long a = givenWork();
            long b = givenWork();
            long c = givenWork();
            givenFavorite(member, a);
            givenFavorite(member, b);
            givenFavorite(member, c);

            assertThat(favoriteWorkIds(member)).containsExactly(c, b, a);
        }

        @Test
        @DisplayName("탭 숫자 2개를 어느 탭에서든 함께 준다 (09 §6 S3) — favorites 는 목록 전체 수와 같다")
        void countsCarryBothTabs() throws Exception {
            Tokens member = loginMember();
            givenFavorite(member, givenWork());
            givenFavorite(member, givenWork());

            JsonNode favorites = favoriteTab(member);
            assertThat(favorites.path("counts").path("favorites").asInt()).isEqualTo(2);
            assertThat(favorites.path("counts").path("downloads").asInt()).isZero();
            assertThat(favorites.path("works").path("totalElements").asInt())
                    .as("카드 숫자와 목록이 어긋나면 사용자는 어느 쪽도 믿지 않는다")
                    .isEqualTo(favorites.path("counts").path("favorites").asInt());

            JsonNode downloads = downloadTab(member);
            assertThat(downloads.path("counts").path("favorites").asInt())
                    .as("받은 악보 탭에 있어도 즐겨찾기 숫자가 보여야 한다")
                    .isEqualTo(2);
            assertThat(downloads.path("counts").path("downloads").asInt()).isZero();
        }

        @Test
        @DisplayName("숨긴 곡은 목록·숫자에서 빠지고, 숨김을 풀면 돌아온다 — 즐겨찾기가 지워진 게 아니다 (8-C 7)")
        void hiddenWorkDisappearsAndComesBack() throws Exception {
            Tokens admin = loginAdmin();
            Tokens member = loginMember();
            long visible = givenWork();
            long hidden = givenWork();
            givenFavorite(member, visible);
            givenFavorite(member, hidden);

            setWorkHidden(admin, hidden, true);
            assertThat(favoriteWorkIds(member)).containsExactly(visible);
            assertThat(favoriteTab(member).path("counts").path("favorites").asInt()).isEqualTo(1);

            setWorkHidden(admin, hidden, false);
            assertThat(favoriteWorkIds(member)).containsExactly(hidden, visible);
            assertThat(favoriteTab(member).path("counts").path("favorites").asInt()).isEqualTo(2);
        }

        @Test
        @DisplayName("20곡 단위 페이지 — page 가 넘어가도 counts 는 전체 수 그대로 (8-C 6)")
        void paging() throws Exception {
            Tokens member = loginMember();
            long a = givenWork();
            long b = givenWork();
            long c = givenWork();
            givenFavorite(member, a);
            givenFavorite(member, b);
            givenFavorite(member, c);

            JsonNode firstPage = favoriteTab(member, "size", "2", "page", "0");
            assertThat(firstPage.path("works").path("content")).hasSize(2);
            assertThat(firstPage.path("works").path("totalElements").asInt()).isEqualTo(3);
            assertThat(firstPage.path("works").path("first").asBoolean()).isTrue();
            assertThat(firstPage.path("works").path("last").asBoolean()).isFalse();

            JsonNode secondPage = favoriteTab(member, "size", "2", "page", "1");
            assertThat(secondPage.path("works").path("content")).hasSize(1);
            assertThat(secondPage.path("works").path("content").get(0).path("id").asLong()).isEqualTo(a);
            assertThat(secondPage.path("counts").path("favorites").asInt())
                    .as("탭 숫자는 페이지와 무관한 전체 수다")
                    .isEqualTo(3);
        }

        @Test
        @DisplayName("다른 계정의 즐겨찾기는 보이지 않는다 — 주소에 사용자 id 를 받는 자리가 없다 (8-C 9)")
        void otherMembersFavoritesAreInvisible() throws Exception {
            Tokens member = loginMember();
            Tokens other = loginOtherMember();
            long mine = givenWork();
            long theirs = givenWork();
            givenFavorite(member, mine);
            givenFavorite(other, theirs);

            assertThat(favoriteWorkIds(member)).containsExactly(mine);
            assertThat(favoriteWorkIds(other)).containsExactly(theirs);
            assertThat(favoriteTab(member).path("counts").path("favorites").asInt()).isEqualTo(1);
        }

        @Test
        @DisplayName("다른 구분의 즐겨찾기는 이 구분의 내 악보에 없다 (기획 05 §5-2)")
        void scopedBySection() throws Exception {
            Tokens member = loginMember();
            long pianoWork = givenWork();
            long violinWork = givenWork();
            moveToSection(violinWork, "VIOLIN");
            givenFavorite(member, pianoWork);
            givenFavorite(member, violinWork);

            assertThat(favoriteWorkIds(member, "section", "PIANO")).containsExactly(pianoWork);
            assertThat(favoriteWorkIds(member))
                    .as("section 을 생략하면 PIANO 다 (§0-7)")
                    .containsExactly(pianoWork);
            assertThat(favoriteWorkIds(member, "section", "VIOLIN")).containsExactly(violinWork);
        }

        @Test
        @DisplayName("정의되지 않은 section 은 400 (§0-7 — 조용히 다른 세계를 보여주지 않는다)")
        void unknownSection_is400() throws Exception {
            Tokens member = loginMember();

            mockMvc.perform(get("/api/me/library/favorites")
                            .param("section", "CELLO")
                            .header("Authorization", bearer(member.accessToken())))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("BUSINESS_RULE_VIOLATION"));
        }

        @Test
        @DisplayName("빈 목록은 200 + content [] (화면이 빈 상태를 그린다 — 8-C 5)")
        void emptyList() throws Exception {
            Tokens member = loginMember();

            JsonNode data = favoriteTab(member);
            assertThat(data.path("works").path("content")).isEmpty();
            assertThat(data.path("works").path("totalElements").asInt()).isZero();
            assertThat(data.path("counts").path("favorites").asInt()).isZero();
        }
    }

    // ==================== §3-3 곡 상세의 favorited ====================

    @Nested
    @DisplayName("§3-3 곡 상세 favorited (09 §6 S5)")
    class WorkDetailFlag {

        @Test
        @DisplayName("비로그인 곡 상세는 200 이고 favorited=false — 공개 API 는 그대로다 (8-B 1)")
        void anonymous_getsFalse() throws Exception {
            long workId = givenWork();

            mockMvc.perform(get("/api/works/{id}", workId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.favorited").value(false));
        }

        @Test
        @DisplayName("즐겨찾기한 사람에게는 true, 다른 사람에게는 false — 버튼이 깜빡이지 않게 곡 상세가 함께 답한다")
        void favoritedFlagIsPerAccount() throws Exception {
            Tokens member = loginMember();
            Tokens other = loginOtherMember();
            long workId = givenWork();
            givenFavorite(member, workId);

            assertThat(workDetail(member, workId).path("favorited").asBoolean()).isTrue();
            assertThat(workDetail(other, workId).path("favorited").asBoolean()).isFalse();

            favoriteOff(member, workId).andExpect(status().isNoContent());
            assertThat(workDetail(member, workId).path("favorited").asBoolean()).isFalse();
        }

        @Test
        @DisplayName("곡 카드(WorkSummaryDTO)에는 즐겨찾기 필드를 두지 않는다 — 1차 제외 (8-G 5)")
        void workSummaryHasNoFavoriteField() throws Exception {
            Tokens member = loginMember();
            long workId = givenReadyWork();
            givenFavorite(member, workId);

            JsonNode card = favoriteTab(member).path("works").path("content").get(0);
            assertThat(card.has("favorited")).isFalse();
            assertThat(card.has("favorite")).isFalse();
        }
    }

    // ==================== given 헬퍼 ====================

    /** 추천 판본이 없는(= 준비 중) 곡 1개. */
    private long givenWork() throws Exception {
        Tokens admin = loginAdmin();
        return createWork(admin, createComposer(admin));
    }

    /** 바로 받기 가능한 곡 1개. */
    private long givenReadyWork() throws Exception {
        Tokens admin = loginAdmin();
        long workId = createWork(admin, createComposer(admin));
        makeReady(admin, workId);
        return workId;
    }

    private JsonNode workDetail(Tokens tokens, long workId) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/works/{id}", workId)
                        .header("Authorization", bearer(tokens.accessToken())))
                .andExpect(status().isOk())
                .andReturn();
        return data(result);
    }

    /**
     * 02 §0-7 — 1차에 곡의 구분을 바꾸는 API 가 <b>설계상 없다</b>. 그래서 given 단계만 컬럼에 직접 쓴다
     * ({@code SectionScopeIntegrationTest} 와 같은 이유·같은 방법). 검증은 그대로 MockMvc 응답 JSON 이다.
     */
    private void moveToSection(long workId, String section) {
        entityManager.flush();
        entityManager.createNativeQuery("UPDATE work SET section = :section WHERE id = :id")
                .setParameter("section", section)
                .setParameter("id", workId)
                .executeUpdate();
        entityManager.clear();
    }
}
