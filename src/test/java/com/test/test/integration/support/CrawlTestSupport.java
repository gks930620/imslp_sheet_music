package com.test.test.integration.support;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.fail;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 비동기 워커({@code imslpExecutor})가 실제로 도는 테스트의 기반.
 *
 * <ul>
 *   <li><b>테스트 트랜잭션 없음</b>({@code NOT_SUPPORTED}) — 워커 스레드가 MockMvc 로 만든 행을 볼 수 있어야 한다.
 *       대신 {@code @AfterEach} 에서 시트뮤직 테이블을 비운다.</li>
 *   <li><b>격리된 H2</b>({@code crawltestdb}) + 시드 적재 끄기 — 시드에 월광이 있으면 CREATE 가 아니라 ATTACH 가 되고,
 *       비우기 작업이 다른 컨텍스트의 시드를 지운다.</li>
 *   <li>IMSLP 대기 시간 0 (03 §3 설정 키), {@link FakeImslpClient} 주입.</li>
 *   <li>완료 대기는 폴링(200ms 간격, 최대 {@link #AWAIT_SECONDS}초) — 새 라이브러리 없음.</li>
 * </ul>
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import(FakeImslpClientConfig.class)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:crawltestdb;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "app.seed.enabled=false",
        "app.imslp.request-interval-ms=0",
        "app.imslp.file-wait-ms=0"
})
public abstract class CrawlTestSupport extends AdminApiTestSupport {

    /** 워커가 PAUSED 상태에서 5초 단위로 자므로(03 §3) 그보다 넉넉하게. */
    protected static final int AWAIT_SECONDS = 15;

    @Autowired
    protected FakeImslpClient fakeImslp;

    /** 그 테스트가 IMSLP 게이트를 어떻게 통과했는지 — 파일당 15초 대기 계약 검증용 (기획 §9-1, 03 §3). */
    @Autowired
    protected ImslpCallLog imslpCalls;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetFakeImslp() {
        fakeImslp.reset();
        imslpCalls.reset();
    }

    @AfterEach
    void stopActiveJobAndCleanTables() {
        fakeImslp.releaseFiles();
        fakeImslp.setAllUnavailable(false);
        try {
            Tokens admin = loginAdmin();
            JsonNode active = data(adminGet(admin, "/api/admin/crawl/jobs/active"));
            if (active != null && !active.isNull() && !active.isMissingNode() && active.has("id")) {
                long jobId = active.path("id").asLong();
                adminPost(admin, "/api/admin/crawl/jobs/{id}/stop", json(), jobId);
                awaitJob(admin, jobId, j -> !isActive(j), "정리용 중지");
            }
        } catch (Throwable ignored) {
            // Red 단계(API 미구현)에서는 실패하는 것이 정상 — 정리 실패가 본 테스트 결과를 가리지 않게 한다.
        }
        try {
            jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY FALSE");
            for (String table : List.of("crawl_item", "crawl_job", "download_log", "edition",
                    "work_alias", "work_catalog_number", "work", "composer_alias", "composer")) {
                jdbcTemplate.execute("TRUNCATE TABLE " + table);
            }
            jdbcTemplate.update("DELETE FROM files WHERE ref_type = 'EDITION'");
        } catch (Throwable ignored) {
            // 테이블이 아직 없는 Red 단계
        } finally {
            try {
                jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY TRUE");
            } catch (Throwable ignored) {
            }
        }
    }

    protected static boolean isActive(JsonNode job) {
        String status = job.path("status").asText();
        return "RUNNING".equals(status) || "PAUSED".equals(status);
    }

    // ===== 수집 API 헬퍼 (§6-2 ~ 6-6) =====

    protected Map<String, Object> item(String url, boolean refresh) {
        return json("url", url, "refresh", refresh);
    }

    protected Map<String, Object> jobBody(boolean fetchFiles, List<Map<String, Object>> items) {
        return json("items", items, "fetchFiles", fetchFiles);
    }

    protected Map<String, Object> jobBody(boolean fetchFiles, String... urls) {
        List<Map<String, Object>> items = new ArrayList<>();
        for (String url : urls) {
            items.add(item(url, false));
        }
        return jobBody(fetchFiles, items);
    }

    protected ResultActions postJob(Tokens tokens, Map<String, Object> body) throws Exception {
        return adminPost(tokens, "/api/admin/crawl/jobs", body);
    }

    /** 작업 생성(201) → CrawlJobDTO. */
    protected JsonNode startJob(Tokens tokens, boolean fetchFiles, String... urls) throws Exception {
        return data(postJob(tokens, jobBody(fetchFiles, urls)).andExpect(status().isCreated()));
    }

    protected JsonNode getJob(Tokens tokens, long jobId) throws Exception {
        return data(adminGet(tokens, "/api/admin/crawl/jobs/{id}", jobId).andExpect(status().isOk()));
    }

    /** 조건을 만족할 때까지 200ms 간격으로 폴링. 시간 초과면 마지막 상태를 붙여 실패. */
    protected JsonNode awaitJob(Tokens tokens, long jobId, Predicate<JsonNode> until, String description) throws Exception {
        long deadline = System.currentTimeMillis() + AWAIT_SECONDS * 1000L;
        JsonNode last = null;
        while (System.currentTimeMillis() < deadline) {
            last = getJob(tokens, jobId);
            if (until.test(last)) {
                return last;
            }
            Thread.sleep(200);
        }
        return fail("%d초 안에 '%s' 가 되지 않음. 마지막 작업 상태: %s", AWAIT_SECONDS, description, last);
    }

    protected JsonNode awaitJobStatus(Tokens tokens, long jobId, String status) throws Exception {
        return awaitJob(tokens, jobId, j -> status.equals(j.path("status").asText()), "status=" + status);
    }

    protected JsonNode awaitEdition(Tokens tokens, long editionId, Predicate<JsonNode> until, String description) throws Exception {
        long deadline = System.currentTimeMillis() + AWAIT_SECONDS * 1000L;
        JsonNode last = null;
        while (System.currentTimeMillis() < deadline) {
            last = getEdition(tokens, editionId);
            if (until.test(last)) {
                return last;
            }
            Thread.sleep(200);
        }
        return fail("%d초 안에 '%s' 가 되지 않음. 마지막 판본 상태: %s", AWAIT_SECONDS, description, last);
    }

    /** 항목(seq) 찾기 — CrawlJobDetailDTO.items */
    protected JsonNode item(JsonNode job, int seq) {
        for (JsonNode item : job.path("items")) {
            if (item.path("seq").asInt() == seq) {
                return item;
            }
        }
        return fail("seq=%d 항목이 없음: %s", seq, job);
    }

    /** AdminWorkDetailDTO.editions 에서 imslpFileId 로 판본 찾기 */
    protected JsonNode editionByImslpFileId(JsonNode workDetail, String imslpFileId) {
        for (JsonNode edition : workDetail.path("editions")) {
            if (imslpFileId.equals(edition.path("imslpFileId").asText())) {
                return edition;
            }
        }
        return fail("imslpFileId=%s 판본이 없음: %s", imslpFileId, workDetail.path("editions"));
    }

    /** URL 1개짜리 작업을 끝까지 돌리고 항목 1의 workId 를 돌려준다. */
    protected long crawlSingle(Tokens tokens, String url, boolean fetchFiles) throws Exception {
        long jobId = startJob(tokens, fetchFiles, url).path("id").asLong();
        JsonNode job = awaitJobStatus(tokens, jobId, "COMPLETED");
        return item(job, 1).path("workId").asLong();
    }
}
