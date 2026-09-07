package com.test.test.integration;

import com.test.test.integration.support.AdminApiTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.greaterThan;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 관리 목록 API 의 {@code size} 쿼리 (02_API_명세서 §4-2·§4-6·§5-8) — qa 결함 D1.
 *
 * <p>계약: {@code size} 는 선택, <b>기본 20</b>, <b>최대 200</b>(초과는 200 으로 자름), 1 미만이면 기본 20.
 * 관리 화면의 작곡가 select 가 {@code ?size=200} 으로 선택지를 한 번에 채우므로(§4-2),
 * 지금처럼 {@code size} 를 무시하고 20개만 주면 <b>21번째 이후 작곡가가 선택지에서 사라진다</b>.
 * 곡·대기함 목록도 같은 규칙으로 맞춘다(관리 화면 세 목록이 서로 다른 페이지 규칙을 갖지 않게).
 */
class AdminListPageSizeIntegrationTest extends AdminApiTestSupport {

    @Test
    @DisplayName("GET /api/admin/composers — size 기본 20 / 지정값 반영 / 200 초과는 200 으로 자름 / 1 미만은 20")
    void composerList_honorsSize() throws Exception {
        Tokens admin = loginAdmin();

        // 기본값: size 를 보내지 않으면 20
        adminGet(admin, "/api/admin/composers")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.size").value(20))
                .andExpect(jsonPath("$.data.content.length()").value(20));

        // 지정값: 5개씩
        adminQuery(admin, "/api/admin/composers", "size", "5")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.size").value(5))
                .andExpect(jsonPath("$.data.content.length()").value(5));

        // 화면이 실제로 쓰는 값 — 시드 작곡가(25명)가 한 번에 다 와야 select 가 채워진다
        adminQuery(admin, "/api/admin/composers", "size", "200")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.size").value(200))
                .andExpect(jsonPath("$.data.content.length()", greaterThan(20)))
                .andExpect(jsonPath("$.data.totalPages").value(1));

        // 상한: 200 초과 요청은 200 으로 자른다
        adminQuery(admin, "/api/admin/composers", "size", "500")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.size").value(200));

        // 1 미만은 기본값으로 (0으로 PageRequest 를 만들면 예외)
        adminQuery(admin, "/api/admin/composers", "size", "0")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.size").value(20));
    }

    @Test
    @DisplayName("GET /api/admin/works — size 기본 20 / 지정값 반영 / 200 초과는 200")
    void workList_honorsSize() throws Exception {
        Tokens admin = loginAdmin();

        adminGet(admin, "/api/admin/works")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.works.size").value(20))
                .andExpect(jsonPath("$.data.works.content.length()").value(20));

        adminQuery(admin, "/api/admin/works", "size", "5")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.works.size").value(5))
                .andExpect(jsonPath("$.data.works.content.length()").value(5));

        adminQuery(admin, "/api/admin/works", "size", "200")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.works.size").value(200))
                .andExpect(jsonPath("$.data.works.content.length()", greaterThan(20)));

        adminQuery(admin, "/api/admin/works", "size", "500")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.works.size").value(200));
    }

    @Test
    @DisplayName("GET /api/admin/copyright/pending — size 기본 20 / 지정값 반영(페이지가 쪼개진다) / 200 초과는 200")
    void copyrightPending_honorsSize() throws Exception {
        Tokens admin = loginAdmin();
        long composerId = createComposer(admin);
        long workId = createWork(admin, composerId);
        createInfoEdition(admin, workId);
        createInfoEdition(admin, workId);

        adminGet(admin, "/api/admin/copyright/pending")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.editions.size").value(20))
                .andExpect(jsonPath("$.data.editions.totalElements").value(2));

        adminQuery(admin, "/api/admin/copyright/pending", "size", "1")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.editions.size").value(1))
                .andExpect(jsonPath("$.data.editions.content.length()").value(1))
                .andExpect(jsonPath("$.data.editions.totalElements").value(2))
                .andExpect(jsonPath("$.data.editions.totalPages").value(2))
                .andExpect(jsonPath("$.data.editions.last").value(false));

        adminQuery(admin, "/api/admin/copyright/pending", "size", "500")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.editions.size").value(200))
                .andExpect(jsonPath("$.data.editions.content.length()").value(2));
    }
}
