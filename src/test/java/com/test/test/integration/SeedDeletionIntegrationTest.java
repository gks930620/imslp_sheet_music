package com.test.test.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.test.test.integration.support.AdminApiTestSupport;
import com.test.test.sheetmusic.seed.SeedCsvReader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 시드 재적재와 삭제 — 01_ERD §6, 03_기술결정 §8·§17.
 *
 * <p><b>왜 지금 문제가 되는가.</b> 시드 로더는 "곡은 {@code imslp_url} 로 보고 없으면 INSERT" 하는 삽입 전용이다.
 * 로컬 DB 가 매 기동 휘발하던 때는 "매번 50곡을 다시 넣는다"가 곧 정상이었다. 그러나 03 §17 로 <b>로컬이 파일 DB</b> 가
 * 되면서 DB 는 재시작을 넘어 살아남고, 로더는 여전히 매 기동 돈다. 그래서 관리자가 §4-9 로 지운 시드 곡이
 * <b>다음 기동에 새 id 로 되살아난다</b> — 삭제가 되돌려지고, 그 곡에 붙어 있던 판본·별칭 작업도 다시 해야 한다.
 * 운영(MySQL)도 같은 구조라 배포 때마다 같은 일이 일어난다.
 *
 * <p><b>계약.</b> 로더는 여전히 삽입 전용·멱등이되, <b>한 번 적재한 CSV 행은 다시 적재하지 않는다</b>
 * (자연키 적재 기록을 남긴다: 곡 {@code imslp_url}, 작곡가 {@code name_original_normalized}).
 * 기록이 이미 있으면 그 행은 건너뛴다 — 지워졌든 남아 있든. CSV 에 <b>새로 추가된 행</b>은 다음 기동에 적재된다.
 * 기존 DB 에는 기록이 없으므로, 첫 실행에서 "이미 있는 행"도 기록만 남기고 넘어가야 한다(중복 삽입 없음).
 */
class SeedDeletionIntegrationTest extends AdminApiTestSupport {

    private static final String SEED_LOADER_BEAN = "seedLoader";
    private static final String DELETED_TITLE = "3 Gymnopédies";

    @Autowired
    private ApplicationContext applicationContext;
    @Autowired
    private SeedCsvReader seedCsvReader;

    /** {@code seed/works.csv} 행 수. 하드코딩 대신 로더가 실제로 읽는 소스를 센다 — 시드가 커져도 안 깨진다. */
    private int seedWorkCount() {
        return seedCsvReader.read("seed/works.csv").size();
    }

    @Test
    @DisplayName("관리자가 지운 시드 곡은 로더 재실행(=재기동)으로 되살아나지 않는다")
    void deletedSeedWork_isNotResurrected() throws Exception {
        int seedWorkCount = seedWorkCount();
        Tokens admin = loginAdmin();
        long workId = findWorkIdByTitle(admin, DELETED_TITLE);

        adminDelete(admin, "/api/admin/works/{id}", workId).andExpect(status().isNoContent());
        assertThat(totalWorks(admin)).isEqualTo(seedWorkCount - 1);

        runSeedLoader();

        adminGet(admin, "/api/admin/works/{id}", workId).andExpect(status().isNotFound());
        assertThat(data(adminQuery(admin, "/api/admin/works", "q", "짐노페디"))
                .path("works").path("content").size())
                .as("지운 시드 곡이 새 id 로 다시 들어오면 안 된다")
                .isZero();
        assertThat(totalWorks(admin)).isEqualTo(seedWorkCount - 1);
    }

    @Test
    @DisplayName("삭제가 없으면 로더 재실행은 여전히 아무것도 바꾸지 않는다 (기존 멱등 계약 유지)")
    void rerunWithoutDeletion_changesNothing() throws Exception {
        Tokens admin = loginAdmin();
        int before = totalWorks(admin);

        runSeedLoader();

        assertThat(totalWorks(admin)).isEqualTo(before).isEqualTo(seedWorkCount());
    }

    private void runSeedLoader() throws Exception {
        applicationContext.getBean(SEED_LOADER_BEAN, ApplicationRunner.class).run(new DefaultApplicationArguments());
    }

    private int totalWorks(Tokens admin) throws Exception {
        return data(adminGet(admin, "/api/admin/works").andExpect(status().isOk()))
                .path("unfilteredTotal").asInt();
    }

    private long findWorkIdByTitle(Tokens admin, String titleOriginal) throws Exception {
        JsonNode content = data(adminQuery(admin, "/api/admin/works", "q", titleOriginal, "size", "50"))
                .path("works").path("content");
        for (JsonNode work : content) {
            if (titleOriginal.equals(work.path("titleOriginal").asText())) {
                return work.path("id").asLong();
            }
        }
        throw new AssertionError("시드 곡을 찾지 못함: " + titleOriginal);
    }
}
