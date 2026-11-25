package com.ceos24.autedi.domain.teacher.dto.response;

import com.ceos24.autedi.domain.user.entity.User;
import com.ceos24.autedi.domain.user.entity.UserStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

@Schema(description = "선생님 상세 조회 응답")
public record TeacherDetailResponse(
        @Schema(description = "선생님 ID", example = "45") Long teacherId,
        @Schema(description = "이름", example = "김선생") String name,
        @Schema(description = "계정 이메일", example = "teacher@autedi.com") String email,
        @Schema(description = "연락처. 등록하지 않았으면 null", example = "010-1234-5678", nullable = true) String phoneNumber,
        @Schema(description = "입사일(YYYY-MM-DD). 등록하지 않았으면 null", example = "2026-03-02", nullable = true)
                LocalDate hireDate,
        @Schema(description = "계정 상태", example = "ACTIVE") UserStatus status) {

    public static TeacherDetailResponse from(User teacher) {
        return new TeacherDetailResponse(
                teacher.getId(),
                teacher.getName(),
                teacher.getEmail(),
                teacher.getPhoneNumber(),
                teacher.getHireDate(),
                teacher.getStatus());
    }
}
