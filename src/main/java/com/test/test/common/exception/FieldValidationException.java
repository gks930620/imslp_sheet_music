package com.test.test.common.exception;

import com.test.test.common.dto.ErrorResponse;
import java.util.List;
import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * 서비스 계층에서 던지는 입력 검증 실패 (02_API_명세서 §0-2 VALIDATION_ERROR).
 *
 * <p>{@code @Valid} 로 표현할 수 없는 규칙(다른 필드와의 비교, 값 형식, 조합 조건)을
 * 같은 응답 형태({@code errors[]} 포함)로 내려주기 위한 예외다.
 */
@Getter
public class FieldValidationException extends BusinessException {

    private final List<ErrorResponse.FieldError> errors;

    public FieldValidationException(List<ErrorResponse.FieldError> errors) {
        super("입력값이 올바르지 않습니다.", HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");
        this.errors = errors;
    }

    public static FieldValidationException of(String field, String message) {
        return of(field, message, null);
    }

    public static FieldValidationException of(String field, String message, Object rejectedValue) {
        return new FieldValidationException(List.of(ErrorResponse.FieldError.builder()
                .field(field)
                .message(message)
                .rejectedValue(rejectedValue)
                .build()));
    }
}
