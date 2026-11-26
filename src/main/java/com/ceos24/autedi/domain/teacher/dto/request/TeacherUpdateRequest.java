package com.ceos24.autedi.domain.teacher.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

@Schema(description = "선생님 정보 수정 요청. 보내지 않았거나 null인 항목은 바꾸지 않으며, 하나 이상은 값이 있어야 한다.")
public record TeacherUpdateRequest(
        @Schema(description = "변경할 이름 (앞뒤 공백은 제거되어 저장)", example = "김민수", nullable = true)
                @Size(max = 50, message = "이름은 50자 이내여야 합니다.")
                @Pattern(regexp = ".*\\S.*", message = "이름은 공백만 입력할 수 없습니다.")
                String name,
        @Schema(
                        description = "변경할 연락처. 숫자·하이픈·+ 7~20자. 빈 문자열(\"\")을 보내면 연락처를 삭제",
                        example = "010-1234-5678",
                        nullable = true)
                @Pattern(regexp = "^$|^[0-9+\\-]{7,20}$", message = "연락처는 숫자, 하이픈, +만 사용한 7~20자여야 합니다.")
                String phoneNumber,
        @Schema(description = "변경할 입사일(YYYY-MM-DD). 미래 날짜도 가능하며 삭제는 지원하지 않음", example = "2026-03-02", nullable = true)
                LocalDate hireDate) {

    /** 바꿀 값이 하나도 없는 요청인지 확인한다. */
    public boolean isEmpty() {
        return name == null && phoneNumber == null && hireDate == null;
    }
}
