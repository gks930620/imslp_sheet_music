package com.test.test.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.test.test.integration.support.AdminApiTestSupport;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 다운로드 기록의 주인은 곡이다 — 01_ERD §3-7 · 02 §4-1 · §5-5 · §4-9 (2026-09-08 확정, qa 3차 결함 8).
 *
 * <p><b>어긋난 사실 두 개.</b> 4번 받은 판본을 지우면 {@code work.download_count} 는 4로 남는데
 * {@code download_log} 행은 0이 된다. 그래서 <b>인기곡 정렬(§3-2, download_count)</b>과
 * <b>대시보드 {@code monthlyDownloads}(§4-1, 로그 수)</b> 가 같은 달의 같은 사건을 다르게 센다
 * (qa 실측: 대시보드가 13 → 9 로 <b>과거를 다시 썼다</b>).
 *
 * <p><b>판정: {@code download_log} 가 원장(사실)이고 {@code work.download_count} 는 그 합계 캐시다.
 * 그리고 다운로드는 <u>곡</u> 단위 사건이다.</b>
 * <ul>
 *   <li>사용자가 받은 것은 "월광 소나타" 이지 "판본 #301" 이 아니다. 판본은 우리가 운영상 교체하는 파일일 뿐이라,
 *       나쁜 스캔을 지우고 다시 넣었다고 곡의 인기가 0 이 되는 것은 <b>사실이 아니다</b>.</li>
 *   <li>운영 지표는 과거를 다시 쓰면 안 된다 — 이번 달 수치가 나중에 <b>줄어드는</b> 지표는 지표가 아니다.</li>
 *   <li>그래서 <b>판본 삭제(§5-5)는 로그를 지우지 않는다</b>({@code edition_id} 만 비운다).
 *       <b>곡 삭제(§4-9)는 로그도 지운다</b> — 로그는 곡에 속한 사실이고, 곡이 사라지면
 *       "무언가를 누가 받았다" 만 남아 아무 질문에도 답하지 못한다({@code work_id} 는 NOT NULL 이다).</li>
 * </ul>
 *
 * <p><b>이 판정이 고치지 <u>않는</u> 것</b>: "판본이 지워져 못 받는 곡이 인기곡 1위" (qa 결함 4-B).
 * 그건 인기곡 목록의 <b>자격 조건</b> 문제이지 기록의 문제가 아니다 — 역사를 지워서 순위를 고치면
 * 대시보드가 다시 틀어진다. 자격 조건은 §3-2 에서 따로 정한다(기획 판단 대기).
 */
class DownloadHistoryRetentionIntegrationTest extends AdminApiTestSupport {

    private static final int DOWNLOADS = 4;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    @DisplayName("판본을 지워도 곡의 다운로드 수·기록은 남는다 — 대시보드 monthlyDownloads 가 줄지 않는다")
    void deletingAnEditionKeepsTheWorksDownloadHistory() throws Exception {
        Tokens admin = loginAdmin();
        long composerId = createComposer(admin);
        long workId = createWork(admin, composerId);
        long editionId = makeReady(admin, workId);

        long monthlyBefore = monthlyDownloads(admin);
        for (int i = 0; i < DOWNLOADS; i++) {
            mockMvc.perform(get("/api/editions/{id}/download", editionId)).andExpect(status().isOk());
        }

        JsonNode work = getWork(admin, workId);
        assertThat(work.path("downloadCount").asLong()).isEqualTo(DOWNLOADS);
        assertThat(work.path("hasDownloadHistory").asBoolean()).isTrue();
        assertThat(monthlyDownloads(admin))
                .as("로그 %d행이 쌓였다", DOWNLOADS)
                .isEqualTo(monthlyBefore + DOWNLOADS);

        adminDelete(admin, "/api/admin/editions/{id}", editionId).andExpect(status().isNoContent());

        JsonNode after = getWork(admin, workId);
        assertThat(after.path("downloadCount").asLong())
                .as("곡의 누적 다운로드는 판본 삭제로 줄지 않는다 — 인기곡 정렬(§3-2)의 근거다")
                .isEqualTo(DOWNLOADS);
        assertThat(after.path("hasDownloadHistory").asBoolean())
                .as("이 곡은 실제로 받아진 적이 있다 — 판본을 지웠다고 없던 일이 되지 않는다")
                .isTrue();
        assertThat(monthlyDownloads(admin))
                .as("대시보드는 '이번 달에 몇 번 받아갔나' 다 — 판본 삭제로 과거 수치가 줄면 지표가 아니다")
                .isEqualTo(monthlyBefore + DOWNLOADS);
        assertThat(after.path("status").asText())
                .as("판본이 사라졌으니 곡은 준비 중으로 돌아간다(01_ERD §4) — 기록과는 별개다")
                .isEqualTo("PREPARING");

        // HTTP 로 드러나지 않는 계약이라 이 한 가지만 DB 로 본다(fileRowExists 와 같은 이유).
        assertThat(logCount(workId, editionId))
                .as("지워진 판본을 계속 가리키면 매달린 참조다 — edition_id 는 NULL 로 비운다(01_ERD §3-7)")
                .isZero();
        assertThat(orphanedLogCount(workId))
                .as("행 자체는 곡에 남는다")
                .isEqualTo(DOWNLOADS);
    }

    @Test
    @DisplayName("곡을 지우면 그 곡의 기록도 사라진다 — 로그는 곡에 속한 사실이다")
    void deletingAWorkAlsoDeletesItsHistory() throws Exception {
        Tokens admin = loginAdmin();
        long composerId = createComposer(admin);
        long workId = createWork(admin, composerId);
        long editionId = makeReady(admin, workId);

        long monthlyBefore = monthlyDownloads(admin);
        for (int i = 0; i < DOWNLOADS; i++) {
            mockMvc.perform(get("/api/editions/{id}/download", editionId)).andExpect(status().isOk());
        }
        assertThat(monthlyDownloads(admin)).isEqualTo(monthlyBefore + DOWNLOADS);

        adminDelete(admin, "/api/admin/works/{id}", workId).andExpect(status().isNoContent());

        assertThat(monthlyDownloads(admin))
                .as("곡이 없으면 그 곡 단위 사실도 남길 자리가 없다(download_log.work_id 는 NOT NULL)")
                .isEqualTo(monthlyBefore);
    }

    /** 그 곡의 로그 중 아직 그 판본을 가리키는 행 수. */
    private long logCount(long workId, long editionId) {
        return entityManager.createQuery(
                        "select count(d) from DownloadLogEntity d where d.workId = :workId and d.editionId = :editionId",
                        Long.class)
                .setParameter("workId", workId)
                .setParameter("editionId", editionId)
                .getSingleResult();
    }

    /** 그 곡의 로그 중 판본이 비워진 행 수. */
    private long orphanedLogCount(long workId) {
        return entityManager.createQuery(
                        "select count(d) from DownloadLogEntity d where d.workId = :workId and d.editionId is null",
                        Long.class)
                .setParameter("workId", workId)
                .getSingleResult();
    }

    private long monthlyDownloads(Tokens admin) throws Exception {
        return data(adminGet(admin, "/api/admin/dashboard").andExpect(status().isOk()))
                .path("monthlyDownloads").asLong();
    }
}
