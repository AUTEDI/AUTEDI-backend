package com.ceos24.autedi.domain.student.dto.request;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

@Schema(description = "학생 등록 요청. 소속 기관은 로그인 정보로 결정합니다.")
public record StudentCreateRequest(
        @Schema(description = "학생 이름. 앞뒤 공백은 제거되어 저장", example = "김학생")
                @NotBlank(message = "학생 이름은 필수 입력값입니다.")
                @Size(max = 50, message = "학생 이름은 50자 이하여야 합니다.")
                String name,
        @Schema(description = "학원 설정에서 사용 중인 학년 ID", example = "3") @NotNull(message = "학년 선택은 필수입니다.") @Positive
                Long gradeId,
        @Schema(description = "같은 학원의 반 ID. 생략·null이면 미배정", example = "1") @Positive Long classId,
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
                String guardianPhone,
        @Schema(description = "같은 이름의 후보를 확인하고 다른 학생으로 신규 등록할 때만 true. 생략·null은 false", defaultValue = "false")
                Boolean confirmDuplicate) {

    public StudentCreateRequest {
        studentPhone = optionalText(studentPhone);
        guardianPhone = optionalText(guardianPhone);
        confirmDuplicate = Boolean.TRUE.equals(confirmDuplicate);
    }

    @JsonAnySetter
    public void rejectUnknownField(String field, Object value) {
        throw new IllegalArgumentException("등록할 수 없는 필드입니다: " + field);
    }

    private static String optionalText(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
