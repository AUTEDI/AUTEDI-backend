package com.ceos24.autedi.domain.generation.controller;

import com.ceos24.autedi.global.exception.ErrorCode;
import com.ceos24.autedi.global.response.ApiResponse;
import java.util.Locale;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 서로 다른 학습지가 동시에 같은 전역 멱등키를 선점하는 경우 DB의 최종 방어를 409로 변환한다. */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = WorksheetGenerationController.class)
public class GenerationRequestConflictHandler {
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Object>> handle(DataIntegrityViolationException exception) {
        ErrorCode code = ErrorCode.INTERNAL_SERVER_ERROR;
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation
                    && violation.getConstraintName() != null
                    && violation
                            .getConstraintName()
                            .toUpperCase(Locale.ROOT)
                            .contains("UK_GENERATION_IDEMPOTENCY_KEY")) {
                code = ErrorCode.GENERATION_REQUEST_CONFLICT;
                break;
            }
        }
        return ResponseEntity.status(code.getStatus()).body(ApiResponse.onFailure(code, null));
    }
}
