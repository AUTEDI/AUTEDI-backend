package com.ceos24.autedi.domain.student.dto.request;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;

@Getter
@Schema(description = "학생 기본 정보 부분 수정. 생략한 필드는 유지하고 선택 필드의 null·빈 값은 삭제합니다.")
public class StudentUpdateRequest {

    @Schema(description = "변경할 학년. 전달 시 null·공백 불가", example = "초등 4")
    @Size(max = 50, message = "학년은 50자 이하여야 합니다.")
    private String grade;

    @Schema(description = "변경할 반. null·빈 값이면 미배정", example = "오전반")
    @Size(max = 100, message = "수강반은 100자 이하여야 합니다.")
    private String className;

    @Schema(description = "변경할 학생 연락처. null·빈 값이면 삭제", example = "010-1234-5678")
    @Size(max = 20, message = "학생 연락처는 20자 이하여야 합니다.")
    @Pattern(
            regexp = "^(?:0[0-9]{8,10}|0[0-9]{1,2}-[0-9]{3,4}-[0-9]{4})$",
            message = "학생 연락처는 국내 전화번호를 숫자 또는 하이픈으로 입력해 주세요.")
    private String studentPhone;

    @Schema(description = "변경할 보호자 연락처. null·빈 값이면 삭제", example = "01012345678")
    @Size(max = 20, message = "보호자 연락처는 20자 이하여야 합니다.")
    @Pattern(
            regexp = "^(?:0[0-9]{8,10}|0[0-9]{1,2}-[0-9]{3,4}-[0-9]{4})$",
            message = "보호자 연락처는 국내 전화번호를 숫자 또는 하이픈으로 입력해 주세요.")
    private String guardianPhone;

    @JsonIgnore
    @Schema(hidden = true)
    private boolean gradeProvided;

    @JsonIgnore
    @Schema(hidden = true)
    private boolean classNameProvided;

    @JsonIgnore
    @Schema(hidden = true)
    private boolean studentPhoneProvided;

    @JsonIgnore
    @Schema(hidden = true)
    private boolean guardianPhoneProvided;

    @JsonSetter("grade")
    public void setGrade(String value) {
        gradeProvided = true;
        grade = value == null ? null : value.strip();
    }

    @JsonSetter("className")
    public void setClassName(String value) {
        classNameProvided = true;
        className = optionalText(value);
    }

    @JsonSetter("studentPhone")
    public void setStudentPhone(String value) {
        studentPhoneProvided = true;
        studentPhone = optionalText(value);
    }

    @JsonSetter("guardianPhone")
    public void setGuardianPhone(String value) {
        guardianPhoneProvided = true;
        guardianPhone = optionalText(value);
    }

    @AssertTrue(message = "학년을 수정할 때는 null이나 공백을 사용할 수 없습니다.")
    @JsonIgnore
    @Schema(hidden = true)
    public boolean isGradeValid() {
        return !gradeProvided || (grade != null && !grade.isBlank());
    }

    @AssertTrue(message = "수정할 필드를 최소 한 개 전달해 주세요.")
    @JsonIgnore
    @Schema(hidden = true)
    public boolean isUpdatePresent() {
        return gradeProvided || classNameProvided || studentPhoneProvided || guardianPhoneProvided;
    }

    @JsonAnySetter
    public void rejectUnknownField(String field, Object value) {
        throw new IllegalArgumentException("수정할 수 없는 필드입니다: " + field);
    }

    private static String optionalText(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
