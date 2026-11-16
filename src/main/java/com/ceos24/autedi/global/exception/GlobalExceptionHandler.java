package com.ceos24.autedi.global.exception;

import com.ceos24.autedi.global.response.ApiResponse;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(CustomException.class)
    public ResponseEntity<ApiResponse<Object>> handleCustomException(CustomException e) {
        ErrorCode errorCode = e.getErrorCode();
        log.warn("CustomException: code={}, message={}", errorCode.getCode(), e.getMessage());
        Object errorDetail =
                (e.getMessage() != null && !e.getMessage().equals(errorCode.getMessage())) ? e.getMessage() : null;
        return ResponseEntity.status(errorCode.getStatus()).body(ApiResponse.onFailure(errorCode, errorDetail));
    }

    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Object>> handleMaxUploadSizeExceededException(
            org.springframework.web.multipart.MaxUploadSizeExceededException e) {
        log.warn("파일 업로드 크기 초과: {}", e.getMessage());
        return ResponseEntity.status(ErrorCode.FILE_TOO_LARGE.getStatus())
                .body(ApiResponse.onFailure(ErrorCode.FILE_TOO_LARGE, "이미지 또는 요청이 업로드 크기 제한을 초과했습니다."));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Object>> handleMethodArgumentNotValidException(
            MethodArgumentNotValidException e) {
        log.warn("Validation failed: {}", e.getMessage());
        List<String> errors = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .toList();

        return ResponseEntity.status(ErrorCode.INVALID_INPUT_VALUE.getStatus())
                .body(ApiResponse.onFailure(ErrorCode.INVALID_INPUT_VALUE, errors));
    }

    @ExceptionHandler({
        MissingServletRequestParameterException.class,
        MethodArgumentTypeMismatchException.class,
        HttpMessageNotReadableException.class
    })
    public ResponseEntity<ApiResponse<Object>> handleBadRequestException(Exception e) {
        log.warn("Bad request: {}", e.getMessage());
        return ResponseEntity.status(ErrorCode.INVALID_INPUT_VALUE.getStatus())
                .body(ApiResponse.onFailure(ErrorCode.INVALID_INPUT_VALUE));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Object>> handleAccessDeniedException(AccessDeniedException e) {
        log.warn("Access denied: {}", e.getMessage());
        return ResponseEntity.status(ErrorCode.ACCESS_DENIED.getStatus())
                .body(ApiResponse.onFailure(ErrorCode.ACCESS_DENIED));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Object>> handleHttpRequestMethodNotSupportedException(
            HttpRequestMethodNotSupportedException e) {
        log.warn("Method not allowed: {}", e.getMessage());
        return ResponseEntity.status(ErrorCode.METHOD_NOT_ALLOWED.getStatus())
                .body(ApiResponse.onFailure(ErrorCode.METHOD_NOT_ALLOWED));
    }

    /**
     * 클라이언트가 먼저 연결을 끊어 비동기 응답(SSE 등)을 더 쓸 수 없는 경우다. 서버 오류가 아니고 쓸 응답도 없으므로 오류 로그와 JSON 오류 응답 없이
     * 넘긴다. HttpServletResponse 인자가 있어야 Spring이 요청을 처리 완료로 본다.
     */
    @ExceptionHandler(AsyncRequestNotUsableException.class)
    public void handleAsyncRequestNotUsable(AsyncRequestNotUsableException e, HttpServletResponse response) {
        log.debug("클라이언트가 먼저 연결을 끊어 비동기 응답을 쓸 수 없습니다: {}", e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> handleException(Exception e) {
        log.error("Unhandled exception occurred", e);
        return ResponseEntity.status(ErrorCode.INTERNAL_SERVER_ERROR.getStatus())
                .body(ApiResponse.onFailure(ErrorCode.INTERNAL_SERVER_ERROR));
    }
}
