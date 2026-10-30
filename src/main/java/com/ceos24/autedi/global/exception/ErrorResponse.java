package com.ceos24.autedi.global.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.validation.BindingResult;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ErrorResponse(String code, String message, LocalDateTime timestamp, List<FieldErrorDetail> errors) {

    public static ErrorResponse of(ErrorCode errorCode) {
        return new ErrorResponse(errorCode.getCode(), errorCode.getMessage(), LocalDateTime.now(), List.of());
    }

    public static ErrorResponse of(ErrorCode errorCode, String message) {
        return new ErrorResponse(errorCode.getCode(), message, LocalDateTime.now(), List.of());
    }

    public static ErrorResponse of(ErrorCode errorCode, BindingResult bindingResult) {
        List<FieldErrorDetail> fieldErrors = bindingResult.getFieldErrors().stream()
                .map(error -> new FieldErrorDetail(
                        error.getField(),
                        error.getRejectedValue() == null
                                ? ""
                                : error.getRejectedValue().toString(),
                        error.getDefaultMessage()))
                .toList();

        return new ErrorResponse(errorCode.getCode(), errorCode.getMessage(), LocalDateTime.now(), fieldErrors);
    }

    public record FieldErrorDetail(String field, String value, String reason) {}
}
