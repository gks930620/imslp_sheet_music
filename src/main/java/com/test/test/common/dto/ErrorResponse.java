package com.test.test.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 에러 응답용 DTO
 * ApiResponse와 통일된 구조 + 에러 상세 정보
 */
@Getter
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {
    private final boolean success = false;  // 항상 false
    private final String message;           // 에러 메시지
    private final String errorCode;         // 에러 코드 (NOT_FOUND, ACCESS_DENIED 등)
    private final LocalDateTime timestamp;  // 발생 시각
    private final List<FieldError> errors;  // 유효성 검증 에러 상세 (선택)

    @Getter
    @AllArgsConstructor
    @Builder
    public static class FieldError {
        private final String field;     // 필드명
        private final String message;   // 에러 메시지
        private final Object rejectedValue; // 거부된 값
    }

    /**
     * 기본 에러 응답 생성
     */
    public static ErrorResponse of(String message, String errorCode) {
        return ErrorResponse.builder()
                .message(message)
                .errorCode(errorCode)
                .timestamp(LocalDateTime.now())
                .build();
    }

    /**
     * 유효성 검증 에러 응답 생성
     */
    public static ErrorResponse of(String message, String errorCode, List<FieldError> errors) {
        return ErrorResponse.builder()
                .message(message)
                .errorCode(errorCode)
                .timestamp(LocalDateTime.now())
                .errors(errors)
                .build();
    }
}

