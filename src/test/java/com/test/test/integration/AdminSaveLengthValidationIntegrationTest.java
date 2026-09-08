package com.test.test.integration;

import com.test.test.integration.support.AdminApiTestSupport;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 관리 저장 API 문자열 길이 검증 (02_API_명세서 §4-4·§4-8·§5-2 의 길이 표, 01_ERD 의 컬럼 길이) — qa 결함 D3.
 *
 * <p>지금은 상한을 넘는 값이 그대로 DB 로 내려가 <b>500</b> 이 된다(컬럼 길이 초과).
 * 계약은 <b>400 {@code VALIDATION_ERROR} + {@code errors[].field}</b> 다 — 화면이 어느 입력이 문제인지 짚어 줄 수 있어야 한다.
 *
 * <p>목록 필드(aliases·catalogNumbers)의 {@code field} 는 Bean Validation 표준대로 {@code aliases[0]} 처럼
 * 인덱스가 붙을 수 있다. 화면은 대괄호 앞부분으로 입력을 찾는다(02 §4-4 주석).
 *
 * <p>경계값(상한과 정확히 같은 길이)은 통과해야 한다 — 상한을 잘못 잡으면 정상 입력이 막힌다.
 */
class AdminSaveLengthValidationIntegrationTest extends AdminApiTestSupport {

    private static String repeat(int length) {
        return "가".repeat(length);
    }

    private static String ascii(int length) {
        return "a".repeat(length);
    }

    // ===== §4-4 작곡가 =====

    @Test
    @DisplayName("작곡가: nameKo 100자 초과 → 400 VALIDATION_ERROR field nameKo (경계값 100자는 201)")
    void composer_nameKo_maxLength() throws Exception {
        Tokens admin = loginAdmin();

        adminPost(admin, "/api/admin/composers", composerBody(repeat(101), "Toolong, Ko " + uniq()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[*].field", hasItem("nameKo")));

        adminPost(admin, "/api/admin/composers", composerBody(repeat(100), "Boundary, Ko " + uniq()))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("작곡가: nameOriginal 200자 초과 → 400 field nameOriginal, aliases 항목 200자 초과 → 400 field aliases")
    void composer_nameOriginal_and_alias_maxLength() throws Exception {
        Tokens admin = loginAdmin();

        adminPost(admin, "/api/admin/composers", composerBody("길이테스트", ascii(201)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[*].field", hasItem("nameOriginal")));

        Map<String, Object> body = composerBody("길이테스트", "Alias, Toolong " + uniq());
        body.put("aliases", List.of(repeat(201)));
        adminPost(admin, "/api/admin/composers", body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[*].field", hasItem(startsWith("aliases"))));
    }

    // ===== §4-8 곡 =====

    @Test
    @DisplayName("곡: titleKo 300자 초과 → 400 field titleKo (경계값 300자는 201)")
    void work_titleKo_maxLength() throws Exception {
        Tokens admin = loginAdmin();
        long composerId = createComposer(admin);

        Map<String, Object> tooLong = workBody(composerId, repeat(301), "Zz Too Long TitleKo " + uniq());
        adminPost(admin, "/api/admin/works", tooLong)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[*].field", hasItem("titleKo")));

        adminPost(admin, "/api/admin/works", workBody(composerId, repeat(300), "Zz Boundary TitleKo " + uniq()))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("곡: movementPageGuide 500자 초과 → 400 field movementPageGuide, aliases 항목 200자 초과 → 400 field aliases")
    void work_movementPageGuide_and_alias_maxLength() throws Exception {
        Tokens admin = loginAdmin();
        long composerId = createComposer(admin);

        Map<String, Object> guide = workBody(composerId, "악장안내 길이", "Zz Guide Too Long " + uniq());
        guide.put("movementPageGuide", repeat(501));
        adminPost(admin, "/api/admin/works", guide)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[*].field", hasItem("movementPageGuide")));

        Map<String, Object> alias = workBody(composerId, "별칭 길이", "Zz Alias Too Long " + uniq());
        alias.put("aliases", List.of(repeat(201)));
        adminPost(admin, "/api/admin/works", alias)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[*].field", hasItem(startsWith("aliases"))));
    }

    /**
     * <b>파생 컬럼이 원문보다 길어져 저장이 깨진다</b> — 2026-09-08 senior-dev, 결함 6 테스트 중 발견.
     *
     * <p>{@code catalogNumbers} 항목 상한은 100자(§0-6)인데, 정렬용 파생값
     * {@code work_catalog_number.sort_key}(01_ERD §3-5)는 숫자 구간을 <b>6자리로 0-패딩</b>한다.
     * {@code "Op.1 Op.1 …"} 처럼 짧은 숫자가 많은 값이면 100자 원문이 350자로 부풀어
     * {@code VARCHAR(120)} 을 넘고, 검증을 통과한 <b>정상 입력이 500</b> 이 된다(qa 결함 D3 와 같은 모양).
     *
     * <p>계약(01_ERD §3-5 개정): <b>{@code sort_key} 는 파생값이므로 저장을 깨뜨리면 안 된다.</b>
     * 컬럼을 {@code VARCHAR(600)} 으로 넓히고(100자 원문의 최대 팽창 350자 + 여유),
     * 그래도 넘치면 <b>잘라서 쓴다</b> — 잘린 정렬 키는 정렬 순서가 뭉개질 뿐 데이터는 잃지 않는다.
     * 반대로 원문 상한을 sort_key 에 맞춰 줄이는 방향은 버린다: 사용자에게 보이는 상한이
     * 내부 정렬 구현 때문에 달라지는 셈이라 설명할 수 없다.
     */
    @Test
    @DisplayName("곡: catalogNumbers 항목 100자 초과 → 400 field catalogNumbers (경계값 100자는 201 — 파생 sort_key 가 넘쳐 500 이 되면 안 된다)")
    void work_catalogNumber_maxLength_andDerivedSortKeyDoesNotOverflow() throws Exception {
        Tokens admin = loginAdmin();
        long composerId = createComposer(admin);

        Map<String, Object> tooLong = workBody(composerId, "작품번호 길이", "Zz Catalog Too Long " + uniq());
        tooLong.put("catalogNumbers", List.of(ascii(101)));
        adminPost(admin, "/api/admin/works", tooLong)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[*].field", hasItem(startsWith("catalogNumbers"))));

        // 숫자가 많아 sort_key 팽창이 큰 99자: "Op.1" 20번 → 정규화 op1…op1 → 패딩 op000001 20개 = 160자
        Map<String, Object> boundary = workBody(composerId, "작품번호 경계", "Zz Catalog Boundary " + uniq());
        boundary.put("catalogNumbers", List.of("Op.1 ".repeat(20).trim()));
        adminPost(admin, "/api/admin/works", boundary)
                .andExpect(status().isCreated());
    }

    // ===== §5-2 / §5-9 판본·판정 =====

    @Test
    @DisplayName("판본: copyrightNote 1000자 초과 → 400 field copyrightNote, publisher 300자 초과 → 400 field publisher")
    void edition_note_and_publisher_maxLength() throws Exception {
        Tokens admin = loginAdmin();
        long composerId = createComposer(admin);
        long workId = createWork(admin, composerId);

        Map<String, Object> note = editionBody(null, null, null, "FREE", repeat(1001));
        adminPost(admin, "/api/admin/works/{workId}/editions", note, workId)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[*].field", hasItem("copyrightNote")));

        Map<String, Object> publisher = editionBody(null, null, null, "FREE", "판정 근거");
        publisher.put("publisher", repeat(301));
        adminPost(admin, "/api/admin/works/{workId}/editions", publisher, workId)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[*].field", hasItem("publisher")));
    }

    @Test
    @DisplayName("판정(§5-9): copyrightNote 1000자 초과 → 400 field copyrightNote")
    void copyrightJudgement_note_maxLength() throws Exception {
        Tokens admin = loginAdmin();
        long composerId = createComposer(admin);
        long workId = createWork(admin, composerId);
        long editionId = createInfoEdition(admin, workId);

        adminPut(admin, "/api/admin/editions/{id}/copyright",
                json("koreaCopyright", "FREE", "copyrightNote", repeat(1001)), editionId)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[*].field", hasItem("copyrightNote")));
    }
    // ===== §6-1 · §6-2 수집 (qa 2차 결함 — 계약 §0-6 의 마지막 두 행) =====

    /**
     * 수집 주소도 다른 저장 API 와 같은 상한을 받는다(02 §0-6). {@code crawl_item.url} 이 VARCHAR(500) 이라
     * 검증이 없으면 D3 와 똑같이 DB 까지 내려가 500 이 된다.
     *
     * <p>{@code urls} 에는 이미 {@code @Size(max = 500)} 이 붙어 있지만 그건 <b>항목 개수</b> 상한이다
     * ("한 번에 500개까지 확인할 수 있어요"). 여기서 요구하는 것은 <b>항목 하나의 길이</b> 상한이고,
     * 그래서 {@code errors[].field} 가 {@code urls[0]} 로 인덱스까지 찍혀야 한다 — 개수 위반과 길이 위반이
     * 같은 필드명으로 오면 화면이 두 문제를 구분하지 못한다.
     */
    @Test
    @DisplayName("수집 확인(§6-1): urls 항목 500자 초과 → 400 field urls[0] \"500자를 넘을 수 없어요\" (경계값 500자는 200)")
    void crawlCheck_url_maxLength() throws Exception {
        Tokens admin = loginAdmin();

        adminPost(admin, "/api/admin/crawl/check", json("urls", List.of(imslpUrlOfLength(501)), "fetchFiles", false))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[*].field", hasItem("urls[0]")))
                .andExpect(jsonPath("$.errors[*].message", hasItem("500자를 넘을 수 없어요")));

        // 경계값: 정확히 500자는 통과해서 정상 판정(NEW)까지 간다.
        adminPost(admin, "/api/admin/crawl/check", json("urls", List.of(imslpUrlOfLength(500)), "fetchFiles", false))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].verdict").value("NEW"));
    }

    /**
     * §6-2 는 §6-1 보다 뒷맛이 나쁘다 — 검증이 없으면 작업(crawl_job)까지 만들어 놓고 항목 저장에서 깨진다.
     * 목록 안 객체의 필드이므로 {@code field} 는 {@code items[0].url} 이다(요청 DTO 에 {@code @Valid} 전파가 필요).
     *
     * <p>경계값(500자)은 "검증을 통과했다" 는 사실만 확인한다 — 유효한 IMSLP 주소로 통과시키면 워커가 실제로 돌기 시작하므로,
     * 형식이 틀린 500자 주소를 써서 <b>처리할 항목 0개 → 400 {@code BUSINESS_RULE_VIOLATION}</b>(§6-2 표)로 받는다.
     * errorCode 가 {@code VALIDATION_ERROR} 가 아니라는 것이 곧 길이 검증을 지나왔다는 증거다.
     */
    @Test
    @DisplayName("수집 시작(§6-2): items[].url 500자 초과 → 400 field items[0].url (경계값 500자는 길이 검증을 통과)")
    void crawlJob_url_maxLength() throws Exception {
        Tokens admin = loginAdmin();

        Map<String, Object> tooLong = json(
                "items", List.of(json("url", imslpUrlOfLength(501), "refresh", false)),
                "fetchFiles", false);
        adminPost(admin, "/api/admin/crawl/jobs", tooLong)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[*].field", hasItem("items[0].url")))
                .andExpect(jsonPath("$.errors[*].message", hasItem("500자를 넘을 수 없어요")));

        Map<String, Object> boundary = json(
                "items", List.of(json("url", nonImslpUrlOfLength(500), "refresh", false)),
                "fetchFiles", false);
        adminPost(admin, "/api/admin/crawl/jobs", boundary)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_RULE_VIOLATION"))
                .andExpect(jsonPath("$.message").value("수집할 주소가 없어요"));
    }

    /** 길이만 다른 IMSLP 작품 주소 — 정규화·판정 규칙(§6-1)을 통과하는 형태. */
    private static String imslpUrlOfLength(int length) {
        String prefix = "https://imslp.org/wiki/";
        return prefix + ascii(length - prefix.length());
    }

    /** IMSLP 주소가 아니어서 INVALID_URL 로 걸러지는(=작업이 만들어지지 않는) 같은 길이의 주소. */
    private static String nonImslpUrlOfLength(int length) {
        String prefix = "https://example.com/";
        return prefix + ascii(length - prefix.length());
    }
}
