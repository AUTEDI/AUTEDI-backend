package com.ceos24.autedi.global.response;

import com.ceos24.autedi.global.response.code.BaseErrorCode;
import com.ceos24.autedi.global.response.code.GeneralSuccessCode;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Schema(description = "AUTEDI 표준 공통 응답 봉투")
@Getter
@AllArgsConstructor
@JsonPropertyOrder({"isSuccess", "code", "message", "result", "error"})
public class ApiResponse<T> {

    @Schema(description = "요청 성공 여부 (true / false)", example = "true")
    @JsonProperty("isSuccess")
    private Boolean isSuccess;

    @Schema(description = "비즈니스 응답/에러 코드", example = "COMMON_200")
    @JsonProperty("code")
    private String code;

    @Schema(description = "사용자 안내 메시지", example = "성공적으로 처리되었습니다.")
    @JsonProperty("message")
    private String message;

    @Schema(description = "성공 시 반환되는 데이터 본문 (실패 시 null)")
    @JsonProperty("result")
    private final T result;

    @Schema(description = "실패 시 에러 상세 객체 (성공 시 null)", example = "null")
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
