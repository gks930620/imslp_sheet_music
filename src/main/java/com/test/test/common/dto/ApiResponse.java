package com.test.test.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@AllArgsConstructor
@Builder
public class ApiResponse<T> {
    private final boolean success;
    private final String message;
    private final T data;

    // 성공 응답 (데이터 + 메시지)
    public static <T> ApiResponse<T> success(String message, T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .message(message)
                .data(data)
                .build();
    }

    // 성공 응답 (데이터만)
    public static <T> ApiResponse<T> success(T data) {
        return success("성공", data);
    }

    // 성공 응답 (메시지만, 데이터 없음)
    public static <T> ApiResponse<T> success(String message) {
        return success(message, null);
    }

    // 실패 응답 (메시지만, 데이터 없음)
    public static <T> ApiResponse<T> fail(String message) {
        return ApiResponse.<T>builder()
                .success(false)
                .message(message)
                .data(null)
                .build();
    }
}

