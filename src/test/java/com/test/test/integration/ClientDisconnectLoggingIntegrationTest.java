package com.test.test.integration;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;
import org.apache.catalina.connector.ClientAbortException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * <b>클라이언트가 먼저 끊은 것은 서버 오류가 아니다</b> — 단절은 DEBUG 한 줄, 응답 본문은 쓰지 않는다
 * (03_기술결정 §20, qa 6차 결함). 반대로 <b>단절이 아닌 I/O 실패는 지금처럼 ERROR + 500</b> 이어야 한다.
 *
 * <p><b>왜 이 테스트가 이런 모양인가 (컨벤션 §6 의 경계 안에서의 선택).</b>
 * 컨벤션 §6 은 백엔드 테스트를 "컨트롤러 통합테스트" 로 한정한다. 이 테스트도 그것이다 —
 * 진짜 {@code DispatcherServlet} → 진짜 {@code ExceptionHandlerExceptionResolver} → 진짜
 * {@link com.test.test.common.exception.GlobalExceptionHandler} 를 통과시키고 <b>응답과 로그만</b> 본다.
 * 다만 <b>소켓 단절 자체는 MockMvc 로 재현할 수 없다</b>({@code MockHttpServletResponse} 는 write 에서 실패하지 않는다).
 * 그래서 단절이 실제로 만들어 내는 <b>예외를 던지는 테스트 전용 엔드포인트</b>를 이 테스트 컨텍스트에만 등록해
 * "그 예외가 핸들러에 도달했을 때 무엇이 남고 무엇이 나가는가" 를 잠근다. 재현 대상은 8084 실측 로그에서 그대로 가져왔다:
 * {@code AsyncRequestNotUsableException: ServletOutputStream failed to write: java.io.IOException: Connection reset by peer}.
 *
 * <p><b>테스트가 잠그지 못하는 것</b>(= 코드리뷰가 볼 것): 실제로 죽은 커넥션에 바이트를 정말 안 쓰는지.
 * 여기서는 "핸들러가 응답 본문을 만들지 않는다" 까지만 관찰할 수 있다. 그거면 2차 예외의 원인은 사라진다 —
 * 실측 로그의 2차 예외가 {@code HttpMessageNotWritableException: No converter for [ErrorResponse] with preset
 * Content-Type 'image/png'} 였고, 이는 <b>본문을 만들려 했기 때문에</b> 난 것이기 때문이다.
 */
class ClientDisconnectLoggingIntegrationTest extends ApiIntegrationTestSupport {

    /** GlobalExceptionHandler 가 로그를 남기는 자리 (컨벤션 §2 — 예외 로깅은 여기 하나로 모은다). */
    private static final String HANDLER_LOG_CATEGORY = "com.test.test.common.exception";

    private Logger handlerLogger;
    private Level previousLevel;
    private ListAppender<ILoggingEvent> logCapture;

    @BeforeEach
    void captureHandlerLog() {
        handlerLogger = (Logger) LoggerFactory.getLogger(HANDLER_LOG_CATEGORY);
        previousLevel = handlerLogger.getLevel();
        handlerLogger.setLevel(Level.DEBUG);

        logCapture = new ListAppender<>();
        logCapture.start();
        handlerLogger.addAppender(logCapture);
    }

    @AfterEach
    void releaseHandlerLog() {
        handlerLogger.detachAppender(logCapture);
        logCapture.stop();
        handlerLogger.setLevel(previousLevel);
    }

    // ===== 단절 — ERROR 로 남기지 않고, 본문도 쓰지 않는다 =====

    /**
     * qa 6차 결함의 결정적 재현({@code curl ... | head -c 2000}) 이 만드는 예외 그대로다.
     * 상태코드가 200 인 것은 "성공했다" 는 뜻이 아니라 <b>핸들러가 상태·본문에 손대지 않았다</b>는 뜻이다 —
     * 커넥션이 이미 죽어 아무것도 클라이언트에 닿지 않으므로, 새 상태코드를 지어내지 않는 것이 계약이다(§20).
     */
    @Test
    @DisplayName("다운로드 중 단절(AsyncRequestNotUsableException): 응답 본문 없음 + ERROR·WARN 로그 없음(DEBUG 한 줄)")
    void client_disconnect_during_download_is_not_an_error() throws Exception {
        String token = loginUser().accessToken();

        mockMvc.perform(get("/api/test-support/throw/{kind}", "async-not-usable")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(content().string(""));

        assertNoErrorLogged();
        assertDebugOneLinerLogged();
    }

    /**
     * 톰캣이 동기 쓰기에서 던지는 형태. 원인 메시지에 "connection reset"·"broken pipe" 가 없어도
     * <b>타입만으로</b> 단절이어야 한다 — 메시지 문자열에만 기대는 구현을 막는다.
     */
    @Test
    @DisplayName("단절(ClientAbortException): 원인 메시지와 무관하게 타입만으로 단절로 본다")
    void tomcat_client_abort_is_not_an_error() throws Exception {
        String token = loginUser().accessToken();

        mockMvc.perform(get("/api/test-support/throw/{kind}", "client-abort")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(content().string(""));

        assertNoErrorLogged();
        assertDebugOneLinerLogged();
    }

    /**
     * 컨테이너·프레임워크가 단절을 <b>감싸서</b> 올려보내는 경우. 겉 타입은 평범한 {@code IOException} 이고
     * 단절이라는 사실은 원인 사슬에만 있다 → 사슬의 가장 구체적인 원인까지 보고 판단해야 한다(§20).
     */
    @Test
    @DisplayName("단절이 원인 사슬 안쪽에 있는 IOException: 감싸여 있어도 단절로 본다")
    void wrapped_connection_reset_is_not_an_error() throws Exception {
        String token = loginUser().accessToken();

        mockMvc.perform(get("/api/test-support/throw/{kind}", "nested-connection-reset")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(content().string(""));

        assertNoErrorLogged();
        assertDebugOneLinerLogged();
    }

    // ===== 경계 — 단절이 아닌 I/O 실패는 삼키지 않는다 (회귀 방지) =====

    /**
     * <b>이 테스트가 §20 의 안전장치다.</b> 디스크 읽기 실패는 서버가 고쳐야 할 진짜 오류다 —
     * 단절 처리를 넓게 잡아 {@code IOException} 전체를 조용히 넘기면 장애가 로그에서 사라진다.
     */
    @Test
    @DisplayName("디스크 I/O 실패(단절 아님): 지금처럼 500 INTERNAL_SERVER_ERROR + ERROR 로그 1건(스택 포함)")
    void real_disk_io_failure_is_still_an_error() throws Exception {
        String token = loginUser().accessToken();

        mockMvc.perform(get("/api/test-support/throw/{kind}", "disk-failure")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("INTERNAL_SERVER_ERROR"));

        assertSingleErrorLoggedWithStackTrace();
    }

    @Test
    @DisplayName("파일 없음(FileNotFoundException, 단절 아님): 500 + ERROR 로그 1건")
    void missing_file_io_failure_is_still_an_error() throws Exception {
        String token = loginUser().accessToken();

        mockMvc.perform(get("/api/test-support/throw/{kind}", "file-missing")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.errorCode").value("INTERNAL_SERVER_ERROR"));

        assertSingleErrorLoggedWithStackTrace();
    }

    // ===== 로그 단언 =====

    private List<ILoggingEvent> loggedEvents() {
        return List.copyOf(logCapture.list);
    }

    private void assertNoErrorLogged() {
        assertThat(loggedEvents())
                .describedAs("클라이언트 단절은 서버 오류가 아니다 — ERROR·WARN 로 남기지 않는다 (03 §20)")
                .noneMatch(event -> event.getLevel().isGreaterOrEqual(Level.WARN));
    }

    private void assertDebugOneLinerLogged() {
        assertThat(loggedEvents())
                .describedAs("단절도 흔적은 남긴다 — DEBUG 한 줄, 스택트레이스 없이 (03 §20)")
                .anySatisfy(event -> {
                    assertThat(event.getLevel()).isEqualTo(Level.DEBUG);
                    assertThat(event.getThrowableProxy()).isNull();
                });
    }

    private void assertSingleErrorLoggedWithStackTrace() {
        List<ILoggingEvent> errors = loggedEvents().stream()
                .filter(event -> event.getLevel() == Level.ERROR)
                .toList();

        assertThat(errors)
                .describedAs("단절이 아닌 실패는 그대로 ERROR 1건 — 단절 처리가 진짜 오류를 삼키면 안 된다 (03 §20)")
                .hasSize(1);
        assertThat(errors.get(0).getThrowableProxy())
                .describedAs("진짜 오류는 원인 추적이 가능해야 한다 — 스택트레이스를 남긴다")
                .isNotNull();
    }

    // ===== 테스트 전용 결함 주입 엔드포인트 (이 테스트 컨텍스트에만 등록된다) =====

    @TestConfiguration
    static class DisconnectFixtureConfig {

        @Bean
        DisconnectFixtureController disconnectFixtureController() {
            return new DisconnectFixtureController();
        }
    }

    /**
     * 소켓을 실제로 끊을 수 없으니, 단절이 만들어 내는 <b>예외를 그 자리에서 던진다</b>.
     * 프로덕션 엔드포인트를 건드리지 않으려고 별도 경로를 쓴다({@code anyRequest().authenticated()} 라 토큰이 필요하다).
     */
    @RestController
    static class DisconnectFixtureController {

        @GetMapping("/api/test-support/throw/{kind}")
        void throwAs(@PathVariable String kind) throws Exception {
            throw switch (kind) {
                // 8084 실측 로그 그대로 (qa 6차 결함)
                case "async-not-usable" -> new AsyncRequestNotUsableException(
                        "ServletOutputStream failed to write: java.io.IOException: Connection reset by peer",
                        new IOException("Connection reset by peer"));
                // 톰캣 동기 쓰기 — 메시지에 단절 문구가 없어도 타입이 답이다
                case "client-abort" -> new ClientAbortException(new IOException("클라이언트가 스트림을 닫았습니다"));
                // 단절이 원인 사슬 안쪽에만 있는 경우
                case "nested-connection-reset" -> new IOException("응답을 쓰지 못했습니다",
                        new IOException("Connection reset by peer"));
                // 단절이 아닌 진짜 I/O 실패 — 여전히 500 + ERROR 여야 한다
                case "disk-failure" -> new IOException("Input/output error: /data/uploads/score.pdf");
                case "file-missing" -> new FileNotFoundException("/data/uploads/score.pdf");
                default -> new IllegalStateException("테스트 픽스처에 없는 kind: " + kind);
            };
        }
    }
}
