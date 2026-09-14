package com.test.test.common.exception;

import com.test.test.common.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.util.DisconnectedClientHelper;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 전역 예외 처리 핸들러
 * Controller에서 발생하는 모든 예외를 처리
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 판본 PDF 업로드 상한 (03 §5). 413 문구를 이 값으로 만든다. */
    @Value("${app.edition.max-file-bytes:104857600}")
    private long maxEditionFileBytes;

    /**
     * 매핑되지 않은 경로 (Spring 6.2: 정적 리소스도 이 예외로 온다).
     * 미구현 API 경로가 500 으로 뭉개지지 않도록 404 를 유지한다 (02 §0-2).
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFound(NoResourceFoundException e) {
        log.debug("No resource: {}", e.getResourcePath());

        ErrorResponse response = ErrorResponse.of("요청한 리소스를 찾을 수 없습니다.", "NOT_FOUND");
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    /**
     * 업로드 상한 초과 (multipart 단계에서 잘린 경우) → 413 (02 §0-2, 03 §5).
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxUploadSize(MaxUploadSizeExceededException e) {
        log.warn("Max upload size exceeded: {}", e.getMessage());

        ErrorResponse response = ErrorResponse.of(
                PayloadTooLargeException.limitMessage(maxEditionFileBytes), "PAYLOAD_TOO_LARGE");
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(response);
    }

    /**
     * 서비스 계층 검증 실패 → 400 VALIDATION_ERROR + errors[] (02 §0-2).
     */
    @ExceptionHandler(FieldValidationException.class)
    public ResponseEntity<ErrorResponse> handleFieldValidation(FieldValidationException e) {
        log.warn("Field Validation Exception: {}", e.getErrors());

        ErrorResponse response = ErrorResponse.of(e.getMessage(), e.getErrorCode(), e.getErrors());
        return ResponseEntity.badRequest().body(response);
    }

    /**
     * 비즈니스 예외 처리 (커스텀 예외들의 부모) — 상태코드는 예외가 들고 있다.
     * - EntityNotFoundException → 404
     * - AccessDeniedException → 403
     * - BusinessRuleException → 400
     * - DuplicateResourceException → 409
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException e) {
        log.warn("Business Exception: {} - {}", e.getErrorCode(), e.getMessage());

        ErrorResponse response = ErrorResponse.of(e.getMessage(), e.getErrorCode());
        return ResponseEntity.status(e.getStatus()).body(response);
    }

    /**
     * 유효성 검증 실패 (@Valid 검증 실패)
     * DTO의 @NotBlank, @Size 등 검증 실패 시 발생
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException e) {
        log.warn("Validation Exception: {}", e.getMessage());

        List<ErrorResponse.FieldError> fieldErrors = e.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> ErrorResponse.FieldError.builder()
                        .field(error.getField())
                        .message(error.getDefaultMessage())
                        .rejectedValue(error.getRejectedValue())
                        .build())
                .collect(Collectors.toList());

        ErrorResponse response = ErrorResponse.of(
                "입력값이 올바르지 않습니다.",
                "VALIDATION_ERROR",
                fieldErrors
        );
        return ResponseEntity.badRequest().body(response);
    }

    /**
     * JSON 파싱 실패 (잘못된 JSON 형식)
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleHttpMessageNotReadable(HttpMessageNotReadableException e) {
        log.warn("JSON Parse Exception: {}", e.getMessage());

        ErrorResponse response = ErrorResponse.of(
                "요청 본문을 읽을 수 없습니다. JSON 형식을 확인해주세요.",
                "INVALID_JSON"
        );
        return ResponseEntity.badRequest().body(response);
    }

    /**
     * 필수 요청 파라미터 누락
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParameter(MissingServletRequestParameterException e) {
        log.warn("Missing Parameter: {}", e.getParameterName());

        ErrorResponse response = ErrorResponse.of(
                "필수 파라미터가 누락되었습니다: " + e.getParameterName(),
                "MISSING_PARAMETER"
        );
        return ResponseEntity.badRequest().body(response);
    }

    /**
     * multipart 필수 파트 누락 (예: {@code file} 없이 §5-1 판본 파일 업로드) → 400 MISSING_PARAMETER (02 §0-2).
     * 핸들러가 없으면 최후의 보루로 떨어져 500 이 된다.
     */
    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ErrorResponse> handleMissingPart(MissingServletRequestPartException e) {
        log.warn("Missing Multipart Part: {}", e.getRequestPartName());

        ErrorResponse response = ErrorResponse.of("파일을 선택해 주세요", "MISSING_PARAMETER");
        return ResponseEntity.badRequest().body(response);
    }

    /**
     * multipart 요청이 아닌데 업로드 API 를 부른 경우 → 400 MISSING_PARAMETER (02 §0-2).
     * 호출자가 보는 사실은 파트 누락과 같다("파일이 서버에 오지 않았다")라 응답도 같게 맞춘다.
     *
     * <p>업로드 상한 초과({@link MaxUploadSizeExceededException})도 이 예외의 하위 타입이지만,
     * Spring 은 더 구체적인 핸들러를 먼저 고르므로 413 은 위의 전용 핸들러가 그대로 처리한다.
     */
    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ErrorResponse> handleMultipart(MultipartException e) {
        log.warn("Multipart Exception: {}", e.getMessage());

        ErrorResponse response = ErrorResponse.of("파일을 선택해 주세요", "MISSING_PARAMETER");
        return ResponseEntity.badRequest().body(response);
    }

    /**
     * 지원하지 않는 HTTP 메서드 → 405 METHOD_NOT_ALLOWED (02 §0-2).
     * RFC 9110 이 405 에 {@code Allow} 헤더를 요구하므로 지원 메서드를 함께 내려준다.
     * 메시지에는 내부 정보(경로·핸들러)를 넣지 않는다.
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        log.warn("Method Not Supported: {}", e.getMethod());

        ErrorResponse response = ErrorResponse.of("지원하지 않는 요청 방식입니다.", "METHOD_NOT_ALLOWED");
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED);

        Set<HttpMethod> supported = e.getSupportedHttpMethods();
        if (supported != null && !supported.isEmpty()) {
            builder.allow(supported.toArray(new HttpMethod[0]));
        }
        return builder.body(response);
    }

    /**
     * 본문 Content-Type 을 처리할 수 없음 → 415 UNSUPPORTED_MEDIA_TYPE (02 §0-2).
     * 업로드의 "multipart 가 아님" 과 달리, Spring 이 실제로 미디어 타입 협상을 하고 거절한 경우다.
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException e) {
        log.warn("Unsupported Media Type: {}", e.getContentType());

        ErrorResponse response = ErrorResponse.of("지원하지 않는 형식입니다.", "UNSUPPORTED_MEDIA_TYPE");
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).body(response);
    }

    /**
     * 파라미터 타입 불일치 (예: Long에 문자열 전달)
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        log.warn("Type Mismatch: {} - {}", e.getName(), e.getValue());

        ErrorResponse response = ErrorResponse.of(
                "파라미터 타입이 올바르지 않습니다: " + e.getName(),
                "TYPE_MISMATCH"
        );
        return ResponseEntity.badRequest().body(response);
    }

    /**
     * IllegalArgumentException 처리 (마이그레이션 중 기존 코드 호환)
     * 추후 커스텀 예외로 모두 변환되면 제거 가능
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException e) {
        log.warn("IllegalArgumentException: {}", e.getMessage());

        // 메시지 내용에 따라 적절한 상태 코드 반환
        HttpStatus status = determineStatusFromMessage(e.getMessage());
        String errorCode = determineErrorCodeFromMessage(e.getMessage());

        ErrorResponse response = ErrorResponse.of(e.getMessage(), errorCode);
        return ResponseEntity.status(status).body(response);
    }

    /**
     * IllegalStateException 처리
     * - 현재 코드에서 의도적으로 던지는 곳은 없음 → 대부분 프레임워크/프로그래밍 오류.
     * - 클라이언트에 내부 메시지를 노출하지 않고 500으로 처리한다.
     * - '충돌' 상황(이미 삭제 등)은 DuplicateResourceException(409)/BusinessRuleException(400) 같은
     *   전용 예외를 사용할 것.
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleIllegalState(IllegalStateException e) {
        log.error("IllegalStateException (예상치 못한 상태): ", e);

        ErrorResponse response = ErrorResponse.of(
                "서버 내부 오류가 발생했습니다.",
                "INTERNAL_SERVER_ERROR"
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    /**
     * I/O 실패 — <b>클라이언트가 먼저 끊은 것은 서버 오류가 아니다</b> (03 §20, qa 6차 결함).
     *
     * <p>미리보기 PNG·PDF 다운로드 중 사용자가 화면을 떠나면 응답을 쓰던 중 단절 예외가 올라온다.
     * 이건 정상 동작이라 ERROR 로 남기지 않고 <b>DEBUG 한 줄(스택 없이)</b>만 남긴다. 응답도 만들지 않는다 —
     * 커넥션이 이미 죽어 아무도 받지 못하는 본문을 만들면 2차 예외(컨버터 선택 실패)만 다시 난다.
     * {@code null} 을 반환하면 {@code HttpEntityMethodProcessor} 가 "처리됨, 본문 없음" 으로 끝낸다
     * (상태코드도 손대지 않는다 — 499 같은 코드를 지어내지 않는다).
     *
     * <p>단절 판별은 스프링이 자기 리졸버에서 쓰는 것과 <b>같은</b> 기준({@link DisconnectedClientHelper})을 쓴다.
     * 우리 문자열 목록을 따로 만들면 같은 상황이 위치에 따라 다르게 로깅된다 (03 §20-1).
     *
     * <p><b>단절이 아닌 I/O 실패</b>(디스크 읽기 실패·파일 없음)는 삼키지 않는다 —
     * 아래 최후의 보루를 그대로 호출해 ERROR + 스택 + 500 을 유지한다 (03 §20-2).
     */
    @ExceptionHandler(IOException.class)
    public ResponseEntity<ErrorResponse> handleIOException(IOException e, HttpServletRequest request) {
        if (DisconnectedClientHelper.isClientDisconnectedException(e)) {
            log.debug("Client disconnected: {} {} - {}: {}",
                    request.getMethod(), request.getRequestURI(),
                    e.getClass().getSimpleName(), e.getMessage());
            return null;
        }
        return handleException(e);
    }

    /**
     * 예상치 못한 예외 처리 (최후의 보루)
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception e) {
        log.error("Unexpected Exception: ", e);

        ErrorResponse response = ErrorResponse.of(
                "서버 내부 오류가 발생했습니다.",
                "INTERNAL_SERVER_ERROR"
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    /**
     * 메시지 내용으로 HTTP 상태 코드 결정 (마이그레이션 용)
     */
    private HttpStatus determineStatusFromMessage(String message) {
        if (message == null) return HttpStatus.BAD_REQUEST;

        if (message.contains("찾을 수 없습니다")) {
            return HttpStatus.NOT_FOUND;
        }
        if (message.contains("본인의") || message.contains("권한이 없습니다")) {
            return HttpStatus.FORBIDDEN;
        }
        return HttpStatus.BAD_REQUEST;
    }

    /**
     * 메시지 내용으로 에러 코드 결정 (마이그레이션 용)
     */
    private String determineErrorCodeFromMessage(String message) {
        if (message == null) return "BAD_REQUEST";

        if (message.contains("찾을 수 없습니다")) {
            return "NOT_FOUND";
        }
        if (message.contains("본인의") || message.contains("권한이 없습니다")) {
            return "ACCESS_DENIED";
        }
        return "BAD_REQUEST";
    }
}

