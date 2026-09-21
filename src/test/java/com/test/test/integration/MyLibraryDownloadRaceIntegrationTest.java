package com.test.test.integration;

import com.test.test.integration.support.NonTransactionalApiTestSupport;
import com.test.test.sheetmusic.member.repository.UserWorkDownloadRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 같은 곡을 <b>같은 순간에</b> 두 번 받을 때 (02 §3-4 · §10-3) — qa 8차 결함 1.
 *
 * <p>받은 악보 upsert 는 "있나 보고(없으면) 넣는다" 라서, 그 사이에 같은 계정의 다른 요청이 끼면 뒤에 온 요청이
 * {@code uk_user_work_download} 를 위반한다. 실제로 겹치는 경로가 있다 — 큰 PDF 를 나란히 받으면
 * 두 요청의 "바이트 확보 → 기록" 이 같은 창에 들어온다(qa 실측: 60ms·200ms 간격이면 안 겹치고,
 * 동시에 쏘면 3/3 재현).
 *
 * <p><b>계약은 200 이다.</b> 다운로드는 즐겨찾기와 달리 "선반에 남기기" 가 목적이 아니라 <b>파일을 주는 것</b>이
 * 목적이고, 선반은 그 부산물이다. 부산물의 경합 때문에 사용자가 받으려던 파일이 500 으로 막히면 안 된다.
 * 그리고 {@code download_log}·{@code download_count} 는 <b>그 요청 것까지 그대로 올라야 한다</b> —
 * 지금은 제약 위반이 다운로드 본체 트랜잭션을 통째로 롤백시켜 집계까지 함께 사라진다(qa 로그).
 *
 * <p>경합은 스레드로 재현하면 뜨는 날·안 뜨는 날이 갈리므로(컨벤션 §6 "순서/랜덤 의존 테스트 금지"),
 * <b>있나 보는 한 번</b>만 "없다" 로 답하게 해 그 순간을 결정적으로 만든다 — 그 뒤의 조회는 실제 DB 를 본다.
 * 먼저 한 번 받아 두는 것은 "겹친 다른 요청이 이미 넣어 버린 줄" 을 대신한다. 거짓말 한 번이 요청을
 * <b>"처음 받는 곡" 갈래</b>로 보내므로, 그 갈래가 겹침을 견디는지를 이 테스트가 본다(03 §26).
 *
 * <p><b>테스트 트랜잭션이 없어야 한다</b>: 제약 위반은 트랜잭션을 rollback-only 로 만들기 때문에,
 * 테스트가 트랜잭션을 열어 둔 채 돌면 운영에서 무슨 일이 벌어지는지 볼 수 없다.
 *
 * @see FavoriteTurnOnRaceIntegrationTest 같은 방식으로 즐겨찾기 켜기의 경합을 고정한 선례
 */
class MyLibraryDownloadRaceIntegrationTest extends NonTransactionalApiTestSupport {

    @SpyBean
    private UserWorkDownloadRepository userWorkDownloadRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("같은 순간 두 번 받아도 200 · 줄은 하나 · 집계는 두 번 다 오른다")
    void concurrentDownloadStillReturnsFileAndCounts() throws Exception {
        Tokens admin = loginAdmin();
        Tokens member = loginUser();
        long composerId = createComposer(admin, "경합작곡가", "Race, Composer");
        ReadyWork ready = createWorkWithRecommendedEdition(admin, composerId, "경합 다운로드곡", "Race Download",
                List.of("Op.1"), List.of(), "INTERMEDIATE", 2, "FREE");

        Instant beforeDownloads = Instant.now().minusSeconds(60);
        download(member, ready.editionId()).andExpect(status().isOk());
        long shelfRowId = shelfRowId(ready.workId());
        Instant firstReceivedAt = timestampOf(shelfRowId, "created_at");

        // "이 사람이 이 곡을 받은 적 있나?" 를 한 번만 '없다' 로 답한다
        // = 그 사이에 같은 계정의 다른 요청이 같은 줄을 넣어 버린 상태. 그 다음 물음부터는 실제 줄을 준다.
        // (두 번째 답을 손으로 만드는 이유: 리포지토리는 인터페이스라 @SpyBean 이 실제 빈에 '위임' 할 뿐
        //  호출할 진짜 메서드가 없다 — doCallRealMethod 가 쓰이지 못한다. findById 는 스텁이 아니라
        //  그대로 실제 조회로 나가므로, 돌려주는 줄은 그 요청의 영속성 컨텍스트가 관리하는 진짜 줄이다.)
        doReturn(Optional.empty())
                .doAnswer(invocation -> userWorkDownloadRepository.findById(shelfRowId))
                .when(userWorkDownloadRepository).findByUserIdAndWorkId(anyLong(), eq(ready.workId()));

        download(member, ready.editionId())
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, containsString(MediaType.APPLICATION_PDF_VALUE)))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("attachment")));

        assertThat(shelfRowCount(ready.workId()))
                .as("줄이 두 개 생기지 않는다(uk_user_work_download)")
                .isEqualTo(1);
        assertThat(downloadLogCount(ready.editionId()))
                .as("집계 원장은 두 번 다 남는다 — 선반 경합이 다운로드 본체 트랜잭션을 롤백시키면 여기서 1 이 된다(03 §24)")
                .isEqualTo(2);
        assertThat(countOf("edition", "download_count", ready.editionId()))
                .as("판본 다운로드 수도 함께 롤백되면 인기곡 정렬이 조용히 틀어진다")
                .isEqualTo(2);
        assertThat(countOf("work", "download_count", ready.workId())).isEqualTo(2);

        assertThat(timestampOf(shelfRowId, "created_at"))
                .as("겹친 요청은 upsert 의 갱신 절반으로 지나간다 — created_at 이 갱신 목록에 들어가면 '처음 받은 시각' 이 매번 덮여 거짓이 된다(01_ERD §3-12 · 03 §26-2)")
                .isEqualTo(firstReceivedAt);
        assertThat(timestampOf(shelfRowId, "last_downloaded_at"))
                .as("반대로 '마지막으로 받은 시각' 은 갱신돼야 한다 — 멈추면 화면의 '오늘 받음' 이 옛날에 머문다")
                .isAfterOrEqualTo(firstReceivedAt);

        MvcResult result = mockMvc.perform(get("/api/me/library/downloads")
                        .param("section", "PIANO")
                        .header(HttpHeaders.AUTHORIZATION, bearer(member.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.counts.downloads").value(1))
                .andExpect(jsonPath("$.data.items.content[0].work.id").value((int) ready.workId()))
                .andExpect(jsonPath("$.data.items.content[0].receivedEdition.id").value((int) ready.editionId()))
                .andExpect(jsonPath("$.data.items.content[0].redownloadState").value("AVAILABLE"))
                .andReturn();

        // 받은 날짜는 "방금 받은 그 시각" 이다 — 화면의 `오늘 받음` 이 이 값에서 나온다(02 §10-3, 09 §1-2).
        // 창이 ±60초로 넓은 것은 의도다: 시계에 기대는 단언이 아니라, 시각이 **엉뚱한 값**(시간대가 밀린 값,
        // 기록되지 않은 옛 값)으로 들어가는 것만 잡는다.
        String downloadedAt = objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("items").path("content").path(0).path("downloadedAt").asText();
        assertThat(Instant.parse(downloadedAt))
                .as("겹친 요청이 남긴 날짜가 밀리거나 비면 '오늘 받음' 이 거짓말이 된다")
                .isBetween(beforeDownloads, Instant.now().plusSeconds(60));
    }

    private ResultActions download(Tokens tokens, long editionId) throws Exception {
        return mockMvc.perform(get("/api/editions/{id}/download", editionId)
                .header(HttpHeaders.AUTHORIZATION, bearer(tokens.accessToken())));
    }

    private long shelfRowId(long workId) {
        return jdbcTemplate.queryForObject(
                "select id from user_work_download where work_id = ?", Long.class, workId);
    }

    /** 응답에 없는 값(특히 {@code created_at})은 표를 직접 본다 — 여기는 테스트 트랜잭션이 없어 커밋된 값이다. */
    private Instant timestampOf(long rowId, String column) {
        Timestamp value = jdbcTemplate.queryForObject(
                "select " + column + " from user_work_download where id = ?", Timestamp.class, rowId);
        return value == null ? null : value.toInstant();
    }

    private Integer shelfRowCount(long workId) {
        return jdbcTemplate.queryForObject(
                "select count(*) from user_work_download where work_id = ?", Integer.class, workId);
    }

    private Integer downloadLogCount(long editionId) {
        return jdbcTemplate.queryForObject(
                "select count(*) from download_log where edition_id = ?", Integer.class, editionId);
    }

    private Integer countOf(String table, String column, long id) {
        return jdbcTemplate.queryForObject(
                "select " + column + " from " + table + " where id = ?", Integer.class, id);
    }
}
