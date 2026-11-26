package com.ceos24.autedi.domain.student.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "학생 등록 요청. 소속 기관은 로그인 정보로 결정합니다.")
public record StudentCreateRequest(
        @Schema(description = "학생 이름. 앞뒤 공백은 제거되어 저장", example = "김학생")
                @NotBlank(message = "학생 이름은 필수 입력값입니다.")
                @Size(max = 50, message = "학생 이름은 50자 이하여야 합니다.")
                String name,
        @Schema(description = "학년. 앞뒤 공백은 제거되어 저장", example = "초등 3")
                @NotBlank(message = "학년은 필수 입력값입니다.")
                @Size(max = 50, message = "학년은 50자 이하여야 합니다.")
                String grade,
        @Schema(description = "수강반. 생략하거나 비어 있으면 미배정", example = "오후반")
                @Size(max = 100, message = "수강반은 100자 이하여야 합니다.")
                String className,
        @Schema(description = "학생 연락처. 국내 전화번호를 숫자 또는 하이픈으로 입력", example = "010-1234-5678")
                @Size(max = 20, message = "학생 연락처는 20자 이하여야 합니다.")
                @Pattern(
                        regexp = "^(?:0[0-9]{8,10}|0[0-9]{1,2}-[0-9]{3,4}-[0-9]{4})$",
                        message = "학생 연락처는 국내 전화번호를 숫자 또는 하이픈으로 입력해 주세요.")
                String studentPhone,
        @Schema(description = "보호자 연락처. 국내 전화번호를 숫자 또는 하이픈으로 입력", example = "01012345678")
                @Size(max = 20, message = "보호자 연락처는 20자 이하여야 합니다.")
                @Pattern(
                        regexp = "^(?:0[0-9]{8,10}|0[0-9]{1,2}-[0-9]{3,4}-[0-9]{4})$",
                        message = "보호자 연락처는 국내 전화번호를 숫자 또는 하이픈으로 입력해 주세요.")
                String guardianPhone) {

    public StudentCreateRequest {
        className = optionalText(className);
        studentPhone = optionalText(studentPhone);
        guardianPhone = optionalText(guardianPhone);
    }

    private static String optionalText(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
