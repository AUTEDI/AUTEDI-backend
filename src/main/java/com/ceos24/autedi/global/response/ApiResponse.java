package com.ceos24.autedi.global.response;

import com.ceos24.autedi.global.response.code.BaseErrorCode;
import com.ceos24.autedi.global.response.code.GeneralSuccessCode;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
@JsonPropertyOrder({"isSuccess", "code", "message", "result", "error"})
public class ApiResponse<T> {

    @JsonProperty("isSuccess")
    private Boolean isSuccess;

    @JsonProperty("code")
    private String code;

    @JsonProperty("message")
    private String message;

    @JsonProperty("result")
    private final T result;

    @JsonProperty("error")
    private Object error;

    // result가 있는 경우 성공 응답
    public static <T> ApiResponse<T> onSuccess(String message, T result) {
        return new ApiResponse<>(true, GeneralSuccessCode.OK.getCode(), message, result, null);
    }

    public static <T> ApiResponse<T> onSuccess(T result) {
        return onSuccess(GeneralSuccessCode.OK.getMessage(), result);
    }

    // result 없는 경우 성공 응답
    public static <T> ApiResponse<T> onSuccess(String message) {
        return new ApiResponse<>(true, GeneralSuccessCode.OK.getCode(), message, null, null);
    }

    public static <T> ApiResponse<T> onSuccess() {
        return onSuccess(GeneralSuccessCode.OK.getMessage());
    }

    // 실패 응답
    public static <T> ApiResponse<T> onFailure(BaseErrorCode errorCode, Object error) {
        return new ApiResponse<>(false, errorCode.getCode(), errorCode.getMessage(), null, error);
    }

    public static <T> ApiResponse<T> onFailure(BaseErrorCode errorCode) {
        return onFailure(errorCode, null);
    }
}
