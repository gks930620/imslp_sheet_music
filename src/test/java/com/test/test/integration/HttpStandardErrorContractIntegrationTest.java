package com.test.test.integration;

import com.test.test.integration.support.AdminApiTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 프레임워크 표준 4xx 를 500 으로 뭉개지 않는다 (02_API_명세서 §0-2, 코드 컨벤션 §2) — qa 2차 결함.
 *
 * <p>{@code GlobalExceptionHandler} 의 최후의 보루 {@code @ExceptionHandler(Exception.class)} 가
 * Spring MVC 표준 예외까지 먼저 삼켜서, "클라이언트가 잘못 부른 요청" 이 전부 500 으로 나간다.
 * 500 은 "서버가 깨졌다" 는 뜻이라 모니터링 신호를 흐리고, 호출자에게 무엇이 잘못됐는지 알려주지 못한다.
 *
 * <ul>
 *   <li>{@code MultipartException}(multipart 요청이 아님) → 400 {@code MISSING_PARAMETER}</li>
 *   <li>{@code HttpRequestMethodNotSupportedException} → 405 {@code METHOD_NOT_ALLOWED} + {@code Allow} 헤더</li>
 *   <li>{@code HttpMediaTypeNotSupportedException} → 415 {@code UNSUPPORTED_MEDIA_TYPE}</li>
 * </ul>
 *
 * <p>이미 초록인 형제 케이스({@code MissingServletRequestPartException} → 400)는
 * {@code AdminEditionApiIntegrationTest#upload_without_file_part_returns_400_missing_parameter} 에 있다.
 */
class HttpStandardErrorContractIntegrationTest extends AdminApiTestSupport {

    // ===== §0-2 · §5-1 — multipart 요청이 아닌 업로드 =====

    /**
     * Content-Type·본문 없이 업로드 API 를 부르면 {@code MultipartException}
     * ("Current request is not a multipart request") 이 난다. 호출자가 보는 사실은
     * "파일이 서버에 오지 않았다" 로 파트 누락과 같으므로 응답도 같다(§0-2 의 근거 참조).
     */
    @Test
    @DisplayName("업로드(§5-1): Content-Type·본문 없이 호출 → 400 MISSING_PARAMETER \"파일을 선택해 주세요\"")
    void upload_without_content_type_returns_400_missing_parameter() throws Exception {
        Tokens admin = loginAdmin();

        mockMvc.perform(post("/api/admin/edition-files")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin.accessToken())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("MISSING_PARAMETER"))
                .andExpect(jsonPath("$.message").value("파일을 선택해 주세요"));
    }

    @Test
    @DisplayName("업로드(§5-1): JSON 으로 호출 → 400 MISSING_PARAMETER (multipart 가 아님)")
    void upload_with_json_body_returns_400_missing_parameter() throws Exception {
        Tokens admin = loginAdmin();

        mockMvc.perform(post("/api/admin/edition-files")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("MISSING_PARAMETER"))
                .andExpect(jsonPath("$.message").value("파일을 선택해 주세요"));
    }

    // ===== §0-2 — 지원하지 않는 HTTP 메서드 =====

    /**
     * 공개 조회 API 에 쓰기 메서드를 보내면 405 다. 비로그인이면 시큐리티가 먼저 401 을 주므로
     * (공개는 {@code GET} 만 permitAll — 02 §0-3), 라우팅 단계까지 가도록 토큰을 붙여 보낸다.
     *
     * <p>{@code Allow} 헤더까지 요구하는 이유는 RFC 9110 이 405 에 그것을 요구하기 때문이다.
     * 예외가 {@code getSupportedMethods()} 로 이미 값을 들고 있어 헤더 한 줄이면 된다.
     */
    @Test
    @DisplayName("공개 곡 API: DELETE·POST → 405 METHOD_NOT_ALLOWED + Allow: GET")
    void unsupported_method_on_public_work_api_returns_405() throws Exception {
        Tokens admin = loginAdmin();

        mockMvc.perform(delete("/api/works/{id}", 21L).header(HttpHeaders.AUTHORIZATION, bearer(admin.accessToken())))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string(HttpHeaders.ALLOW, containsString("GET")))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("METHOD_NOT_ALLOWED"))
                .andExpect(jsonPath("$.message").isString());

        mockMvc.perform(post("/api/works/{id}", 21L)
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.errorCode").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    @DisplayName("공개 작곡가 API: PUT → 405 METHOD_NOT_ALLOWED")
    void unsupported_method_on_public_composer_api_returns_405() throws Exception {
        Tokens admin = loginAdmin();

        mockMvc.perform(put("/api/composers/{id}", 4L)
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string(HttpHeaders.ALLOW, containsString("GET")))
                .andExpect(jsonPath("$.errorCode").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    @DisplayName("채팅방 API: PATCH → 405 METHOD_NOT_ALLOWED")
    void unsupported_method_on_room_api_returns_405() throws Exception {
        Tokens user = loginUser();

        mockMvc.perform(patch("/api/rooms/{id}", 1L)
                        .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.errorCode").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    @DisplayName("관리 API: DELETE /api/admin/dashboard → 405 METHOD_NOT_ALLOWED")
    void unsupported_method_on_admin_api_returns_405() throws Exception {
        Tokens admin = loginAdmin();

        adminDelete(admin, "/api/admin/dashboard")
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string(HttpHeaders.ALLOW, containsString("GET")))
                .andExpect(jsonPath("$.errorCode").value("METHOD_NOT_ALLOWED"));
    }

    // ===== §0-2 — 본문 Content-Type 이 맞지 않음 =====

    /**
     * 여기서만 415 를 쓴다 — Spring 이 실제로 미디어 타입 협상을 하고 거절한 경우다
     * ({@code HttpMediaTypeNotSupportedException}). 업로드의 "multipart 가 아님" 과 구분되는 지점.
     */
    @Test
    @DisplayName("JSON API 를 text/plain 으로 호출 → 415 UNSUPPORTED_MEDIA_TYPE")
    void unsupported_content_type_returns_415() throws Exception {
        Tokens admin = loginAdmin();

        mockMvc.perform(post("/api/admin/composers")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin.accessToken()))
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("nameKo=베토벤"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("UNSUPPORTED_MEDIA_TYPE"))
                .andExpect(jsonPath("$.message").isString());
    }
}
