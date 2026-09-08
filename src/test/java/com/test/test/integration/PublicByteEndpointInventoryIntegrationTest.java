package com.test.test.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.test.test.integration.support.AdminApiTestSupport;
import jakarta.servlet.http.HttpServletResponse;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.core.MethodParameter;
import org.springframework.core.ResolvableType;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpEntity;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.handler.SimpleUrlHandlerMapping;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.RequestMappingInfoHandlerMapping;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.springframework.web.servlet.resource.ResourceHttpRequestHandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * 판본 PDF 바이트가 나갈 수 있는 <b>공개 경로의 전수</b> 계약 — 02_API_명세서 §0-4 · §3-4
 * (2026-09-08 senior-dev, qa 4차 결함 1).
 *
 * <h2>왜 경로별 테스트로는 부족한가</h2>
 * §3-4 는 {@code GET /api/editions/{id}/download} 가 판본 바이트를 얻는 <b>유일한</b> 공개 경로라고
 * 세 번에 걸쳐 못 박았는데, 실제로는 세 번 다 다른 문이 열려 있었다.
 * <ol>
 *   <li>1차: {@code /api/files/{id}/content} · {@code /api/files} · {@code /api/files/paths}</li>
 *   <li>2차: {@code /uploads/{저장파일명}} ({@link UploadsServingGateIntegrationTest})</li>
 *   <li>3차: {@code /images/{저장파일명}} ({@link ImagesLegacyPathIntegrationTest}) — 실데이터 502MB 전수 200</li>
 * </ol>
 * 매번 <b>알려진 경로 하나</b>를 막는 테스트를 붙였고, 그래서 매번 다음 문이 남았다.
 * 경로를 손으로 열거하는 테스트는 <b>이미 아는 문만</b> 지킨다 — 네 번째 문은 못 막는다.
 *
 * <h2>그래서 무엇을 계약으로 박는가</h2>
 * 경로 목록을 사람이 적지 않고 <b>스프링의 핸들러 매핑에서 뽑는다.</b> 새 엔드포인트·새 정적 핸들러가
 * 생기면 그 즉시 이 테스트의 검사 대상이 된다.
 * <ul>
 *   <li><b>A. 인벤토리</b> — 바이트(={@code Resource}/{@code byte[]}/{@code InputStream}/
 *       {@code StreamingResponseBody}/{@code HttpServletResponse} 직접 쓰기)를 실어 나를 수 있는
 *       <b>GET 핸들러 목록</b>이 아래 목록과 <b>정확히 같아야</b> 한다. 우리 코드 몫은
 *       {@link #PRODUCT_BYTE_ENDPOINTS} 3개뿐이고, 프레임워크·라이브러리 몫은
 *       {@link #FRAMEWORK_BYTE_ENDPOINTS} 로 따로 못 박는다. 새 바이트 엔드포인트를 추가하려면
 *       명세서 §0-4 표와 이 목록을 함께 고쳐야 한다 — "게이트를 빠뜨렸다"가 아니라
 *       "계약에 없는 문을 열었다"로 먼저 걸린다.</li>
 *   <li><b>B. 전수 스윕</b> — 실제 판본 PDF 를 하나 만들고, <b>매핑에서 뽑은 모든 GET 경로</b>(정적 리소스
 *       핸들러 포함)에 그 판본의 저장 파일명·files.id·edition.id 를 끼워 <b>비로그인</b>으로 요청한다.
 *       {@code %PDF} 바이트를 돌려주는 경로가 {@code /api/editions/{id}/download} 말고 하나라도 있으면 실패다.
 *       A 를 우회해 목록에 이름만 올려도(또는 {@code void} + {@code response.getOutputStream()} 으로
 *       타입 검사를 피해도) 여기서 걸린다.</li>
 * </ul>
 *
 * <h2>컨벤션 §6 아래에서 인정하는 예외인가 — 그렇다</h2>
 * 컨벤션 §6 은 "컨트롤러 통합테스트만"이고 이 테스트도 <b>@SpringBootTest + MockMvc 로 실제 HTTP 를 치는</b>
 * 컨트롤러 통합테스트다. 다른 점은 <b>요청 목록을 손으로 적지 않고 핸들러 매핑에서 도출</b>한다는 것뿐이다.
 * {@link SchemaEnumColumnTypeIntegrationTest}(DDL 을 직접 검사)와 같은 성격의 예외로 본다 —
 * 그 테스트가 "엔티티가 만드는 DDL" 을 봐야만 잡히는 사고를 다뤘듯, 이 테스트는
 * <b>"아직 아무도 이름을 모르는 엔드포인트"</b> 를 다룬다. 검사 대상이 개별 응답이 아니라
 * <b>애플리케이션의 표면(surface) 그 자체</b>라서, 표면을 열거하지 않고는 표현할 방법이 없다.
 * 서비스·DAO 를 목킹해 계층을 쪼개는 단위테스트가 아니므로 §6 의 금지에는 해당하지 않는다.
 */
class PublicByteEndpointInventoryIntegrationTest extends AdminApiTestSupport {

    private static final String PDF_MAGIC = "%PDF";

    /** 우리가 짜고 우리가 지울 수 있는 코드의 경계. 이 밖은 프레임워크·라이브러리 몫이다. */
    private static final String PRODUCT_PACKAGE = "com.test.test";

    /** 판본 바이트가 나가도 되는 <b>단 하나</b>의 경로 (02 §3-4). 스윕에서 제외하고 따로 검증한다. */
    private static final String EDITION_DOWNLOAD = "/api/editions/{id}/download";

    /**
     * <b>우리 코드가</b> 바이트를 실어 나를 수 있는 GET 핸들러 전수 (02 §0-4). <b>여기 없는 것이 생기면 실패다.</b>
     *
     * <ul>
     *   <li>{@code /api/editions/{id}/download} — 판본 PDF 의 유일한 공개 경로. 숨김 404 → 파일 없음 404
     *       → 비 FREE 403 → 바이트 없음 503 게이트를 태우고 다운로드 수를 센다(§3-4).</li>
     *   <li>{@code /uploads/{storedFileName:.+}} — 미리보기 PNG·커뮤니티·사용자 파일 전용 프록시.
     *       판본 PDF 와 {@code files} 행 없는 이름은 404(§0-4 표).</li>
     *   <li>{@code /api/files/{fileId}/content} — 커뮤니티 첨부 다운로드. {@code ref_type=EDITION} 은 404(§3-4).</li>
     * </ul>
     * {@code /images/{filename:.+}} 는 <b>없다</b> — 폐기 확정({@link ImagesLegacyPathIntegrationTest}).
     */
    private static final Set<String> PRODUCT_BYTE_ENDPOINTS = Set.of(
            EDITION_DOWNLOAD,
            "/uploads/{storedFileName:.+}",
            "/api/files/{fileId}/content");

    /**
     * 우리 코드지만 <b>응답 본문에 바이트를 쓰지 않는</b> 핸들러 — 탐지기가 타입만으로는 구분하지 못해 이름으로 뺀다.
     *
     * <p>{@code Oauth2LoginController#oauth2LoginWeb} 은 {@code void} + {@code HttpServletResponse} 로
     * {@code sendRedirect} 만 한다. 시그니처는 "바이트를 쓸 수 있는" 모양이라 A 의 탐지기에 걸리지만
     * 본문이 없다(스윕 B 가 실제로 바이트가 안 나옴을 매 실행 확인한다). 시그니처가 아니라 <b>이름</b>으로
     * 빼는 이유: 같은 모양의 핸들러가 새로 생기면 그때는 걸려야 하기 때문이다.
     */
    private static final Set<String> PRODUCT_NON_BODY_HANDLERS = Set.of(
            "/custom-oauth2/login/web/{provider}");

    /**
     * 프레임워크·라이브러리가 여는 바이트 GET 핸들러 — <b>우리가 지울 수 없지만 표면의 일부</b>다.
     *
     * <p>여기까지 못 박는 이유: {@code /images} 사고의 원인이 "보일러플레이트가 남긴, 아무도 안 보던 컨트롤러"였다.
     * 의존성이 새 바이트 엔드포인트를 열면(springdoc·actuator 업그레이드, 새 스타터 추가) 이 목록이 어긋나
     * <b>도입 시점에</b> 검토를 강제한다. 각 항목이 판본 바이트와 무관한 근거:
     * <ul>
     *   <li>{@code /error} — {@code BasicErrorController}. 오류 본문(HTML/JSON)만 쓴다. 업로드 저장소를 읽지 않는다.</li>
     *   <li>{@code /actuator}·{@code /actuator/health}(+ 하위) — 상태 JSON. 노출은 health·info·metrics 로
     *       제한되고(application.yml) 나머지는 인증이 필요하다(SecurityConfig).</li>
     *   <li>{@code /v3/api-docs}(+{@code .yaml}) — springdoc 이 만드는 OpenAPI 문서 바이트. 저장소를 읽지 않는다.</li>
     * </ul>
     */
    private static final Set<String> FRAMEWORK_BYTE_ENDPOINTS = Set.of(
            "/error",
            "/actuator",
            "/actuator/health",
            "/actuator/health/**",
            "/v3/api-docs",
            "/v3/api-docs.yaml");

    /** 스윕이 <b>비어 돌면</b> 언제나 초록이다. 매핑 열거가 깨졌는지 보는 하한선(현재 약 50개). */
    private static final int MIN_SWEPT_PATTERNS = 30;

    @Autowired
    private ApplicationContext applicationContext;

    // ===== A. 인벤토리 =====

    @Test
    @DisplayName("바이트를 반환할 수 있는 GET 핸들러는 계약에 적힌 3개뿐이다 — 네 번째 문이 생기면 여기서 먼저 걸린다")
    void byteReturningGetHandlersMatchTheContract() {
        Set<String> ours = new TreeSet<>();
        Set<String> whole = new TreeSet<>();
        for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : handlerMethods().entrySet()) {
            if (!isGetCapable(entry.getKey()) || !carriesBytes(entry.getValue())) {
                continue;
            }
            Set<String> patterns = patternsOf(entry.getKey());
            whole.addAll(patterns);
            if (entry.getValue().getBeanType().getName().startsWith(PRODUCT_PACKAGE)) {
                ours.addAll(patterns);
            }
        }
        ours.removeAll(PRODUCT_NON_BODY_HANDLERS);

        assertThat(ours)
                .as("""
                        우리 코드가 바이트(Resource/byte[]/InputStream/StreamingResponseBody/HttpServletResponse)를
                        내보낼 수 있는 GET 엔드포인트. 새 항목이 보이면 게이트를 붙이는 것으로 끝내지 말고
                        02_API_명세서 §0-4 표에 먼저 올려라 — 아는 경로만 막아 온 결과가 qa 1·2·3·4차의 같은 결함이다.""")
                .containsExactlyInAnyOrderElementsOf(PRODUCT_BYTE_ENDPOINTS);

        Set<String> expectedSurface = new TreeSet<>(PRODUCT_BYTE_ENDPOINTS);
        expectedSurface.addAll(PRODUCT_NON_BODY_HANDLERS);
        expectedSurface.addAll(FRAMEWORK_BYTE_ENDPOINTS);
        assertThat(whole)
                .as("""
                        우리 것 + 프레임워크·라이브러리 것을 합친 바이트 GET 표면 전체.
                        의존성이 새 바이트 엔드포인트를 열어도(= 우리가 한 줄도 안 짜도) 여기서 걸려야 한다 —
                        /images 사고가 바로 보일러플레이트가 남긴, 아무도 안 보던 컨트롤러였다.""")
                .containsExactlyInAnyOrderElementsOf(expectedSurface);
    }

    // ===== B. 전수 스윕 =====

    @Test
    @DisplayName("비로그인 전수 스윕: 매핑된 모든 GET 경로에 판본 식별자를 끼워 넣어도 PDF 바이트가 나오는 곳은 다운로드 API 하나뿐")
    void noMappedGetPathLeaksEditionPdfBytes() throws Exception {
        Tokens admin = loginAdmin();
        EditionFixture fixture = createFreeEditionWithFile(admin);

        // 열려 있어야 하는 단 하나의 문 — 여기가 막히면 제품이 죽는다.
        byte[] allowed = mockMvc.perform(get(EDITION_DOWNLOAD, fixture.editionId()))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(looksLikePdf(allowed))
                .as("§3-4 의 정식 경로는 그대로 PDF 를 준다")
                .isTrue();

        Set<String> swept = sweepablePatterns();
        // 스윕이 비면 이 테스트는 아무것도 안 하고 초록이 된다 — 열거가 살아 있는지 먼저 확인한다.
        assertThat(swept)
                .as("매핑 열거가 살아 있는가(스윕 대상 경로 수)")
                .hasSizeGreaterThanOrEqualTo(MIN_SWEPT_PATTERNS)
                .as("실제로 뒷문이었던 경로들이 스윕에 들어 있는가 — 이게 빠지면 스윕은 뜻이 없다")
                .contains("/uploads/{storedFileName:.+}", "/api/files/{fileId}/content");

        // 저장 파일명·files.id·edition.id — 뒷문을 여는 데 쓰이는 세 가지 값 (qa 는 uploads/ 폴더를 훑어 이름을 얻었다)
        List<String> candidates = List.of(
                fixture.pdfStoredFileName(),
                String.valueOf(fixture.pdfFileId()),
                String.valueOf(fixture.editionId()));

        List<String> leaks = new ArrayList<>();
        for (String pattern : swept) {
            for (String candidate : candidates) {
                String url = fill(pattern, candidate);
                byte[] body = mockMvc.perform(get(url)).andReturn().getResponse().getContentAsByteArray();
                if (looksLikePdf(body)) {
                    leaks.add(pattern + "  →  " + url);
                }
            }
        }

        assertThat(leaks)
                .as("""
                        비로그인 GET 으로 판본 PDF 바이트가 나온 경로.
                        판본 바이트의 공개 경로는 GET /api/editions/{id}/download 하나여야 한다(02 §3-4) —
                        게이트를 붙이든 경로를 지우든, 여기 이름이 남아 있으면 계약 위반이다.""")
                .isEmpty();
    }

    // ===== 매핑 열거 =====

    /**
     * 매핑된 모든 핸들러 메서드 — <b>{@code RequestMappingInfoHandlerMapping} 빈 전부</b>를 합친다.
     *
     * <p>{@code getBean(RequestMappingHandlerMapping.class)} 로는 안 된다. actuator 가
     * {@code controllerEndpointHandlerMapping} 을 같은 타입으로 하나 더 등록해
     * {@code NoUniqueBeanDefinitionException} 이 난다. {@code @Qualifier("requestMappingHandlerMapping")} 로
     * 하나만 집으면 예외는 사라지지만 <b>actuator 의 {@code /actuator/**} 3개가 스윕에서 통째로 빠진다</b> —
     * 표면 전체를 훑는다는 이 테스트의 전제를 스스로 깨는 선택이라 택하지 않았다.
     * 상위 타입({@code RequestMappingInfoHandlerMapping})으로 전부 모으면 actuator 의 두 종류
     * ({@code WebMvcEndpointHandlerMapping} · {@code ControllerEndpointHandlerMapping})가 모두 들어온다.
     */
    private Map<RequestMappingInfo, HandlerMethod> handlerMethods() {
        Map<RequestMappingInfo, HandlerMethod> merged = new LinkedHashMap<>();
        applicationContext.getBeansOfType(RequestMappingInfoHandlerMapping.class).values()
                .forEach(mapping -> merged.putAll(mapping.getHandlerMethods()));
        return merged;
    }

    /**
     * 스윕 대상 경로 — {@code @RequestMapping} GET 핸들러 + <b>정적 리소스 핸들러</b>({@code /assets/**} 등).
     *
     * <p>정적 핸들러를 함께 훑는 이유: 이 저장소는 실제로 {@code WebConfig.addResourceHandlers} 로
     * {@code /uploads/**} → 업로드 디렉터리를 정적 서빙한 적이 있다(지금은 프록시로 교체). 누군가
     * {@code addResourceLocations("file:./uploads/")} 를 다시 등록하면 컨트롤러는 하나도 안 늘어난 채
     * 판본 PDF 가 전량 열린다 — 컨트롤러만 훑는 테스트로는 보이지 않는 문이다.
     */
    private Set<String> sweepablePatterns() {
        Set<String> patterns = new LinkedHashSet<>();
        for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : handlerMethods().entrySet()) {
            if (isGetCapable(entry.getKey())) {
                patterns.addAll(patternsOf(entry.getKey()));
            }
        }
        for (SimpleUrlHandlerMapping mapping : applicationContext.getBeansOfType(SimpleUrlHandlerMapping.class).values()) {
            mapping.getHandlerMap().forEach((pattern, handler) -> {
                if (handler instanceof ResourceHttpRequestHandler) {
                    patterns.add(pattern);
                }
            });
        }
        // 유일하게 허용된 문은 따로 검증했다(스윕에 남기면 그 문에서 나온 PDF 를 누출로 센다).
        patterns.remove(EDITION_DOWNLOAD);
        return patterns;
    }

    private static Set<String> patternsOf(RequestMappingInfo info) {
        if (info.getPathPatternsCondition() != null) {
            return info.getPathPatternsCondition().getPatternValues();
        }
        if (info.getPatternsCondition() != null) {
            return info.getPatternsCondition().getPatterns();
        }
        return Set.of();
    }

    /** 메서드 조건이 비어 있으면 모든 메서드를 받는다 — GET 도 받는다는 뜻이다. */
    private static boolean isGetCapable(RequestMappingInfo info) {
        Set<RequestMethod> methods = info.getMethodsCondition().getMethods();
        return methods.isEmpty() || methods.contains(RequestMethod.GET);
    }

    /**
     * 이 핸들러가 원시 바이트를 실어 나를 수 있는가.
     * 반환 타입뿐 아니라 <b>{@code HttpServletResponse}·{@code OutputStream} 파라미터</b>도 본다 —
     * {@code void} 로 선언하고 응답 스트림에 직접 쓰면 반환 타입 검사만으로는 안 보인다.
     */
    private static boolean carriesBytes(HandlerMethod handler) {
        for (MethodParameter parameter : handler.getMethodParameters()) {
            Class<?> type = parameter.getParameterType();
            if (HttpServletResponse.class.isAssignableFrom(type)
                    || OutputStream.class.isAssignableFrom(type)
                    || Writer.class.isAssignableFrom(type)) {
                return true;
            }
        }
        MethodParameter returnType = handler.getReturnType();
        Class<?> type = returnType.getParameterType();
        if (isByteCarrier(type)) {
            return true;
        }
        if (HttpEntity.class.isAssignableFrom(type)) {
            Class<?> body = ResolvableType.forMethodParameter(returnType).getGeneric(0).resolve();
            // 제네릭을 못 풀면(ResponseEntity<?>) 무엇이든 담을 수 있으므로 바이트로 본다 — 안전한 쪽으로 실패시킨다.
            return body == null || isByteCarrier(body);
        }
        return false;
    }

    private static boolean isByteCarrier(Class<?> type) {
        return Resource.class.isAssignableFrom(type)
                || byte[].class.equals(type)
                || InputStream.class.isAssignableFrom(type)
                || StreamingResponseBody.class.isAssignableFrom(type)
                || Object.class.equals(type);
    }

    /** 경로 변수({@code {id}}, {@code {name:.+}})와 와일드카드({@code **}, {@code *})를 후보 값으로 채운다. */
    private static String fill(String pattern, String value) {
        String quoted = Matcher.quoteReplacement(value);
        return pattern
                .replaceAll("\\{[^{}]*}", quoted)
                .replace("**", value)
                .replaceAll("(?<=/)\\*", quoted);
    }

    private static boolean looksLikePdf(byte[] body) {
        return body != null && body.length > 0
                && new String(body, StandardCharsets.ISO_8859_1).contains(PDF_MAGIC);
    }

    // ===== 픽스처 =====

    /** 판본 1개(파일 있음·FREE) + 뒷문을 여는 데 쓰이는 식별자들. */
    private record EditionFixture(long editionId, long pdfFileId, String pdfStoredFileName) {
    }

    private EditionFixture createFreeEditionWithFile(Tokens admin) throws Exception {
        long workId = createWork(admin, createComposer(admin));
        JsonNode upload = uploadSamplePdf(admin);
        long pdfFileId = upload.path("fileId").asLong();
        long editionId = createEdition(admin, workId, editionBody(pdfFileId,
                upload.path("previewFileId").asLong(), upload.path("pageCount").asInt(),
                "FREE", "테스트 판정 근거"));
        setRecommended(admin, workId, editionId);

        String webPath = storedWebPath(pdfFileId);
        return new EditionFixture(editionId, pdfFileId, webPath.substring(webPath.lastIndexOf('/') + 1));
    }
}
