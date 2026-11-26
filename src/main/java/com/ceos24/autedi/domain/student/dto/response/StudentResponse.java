package com.ceos24.autedi.domain.student.dto.response;

import com.ceos24.autedi.domain.student.entity.Student;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "학생 등록·상세 조회·기본 정보 수정 응답")
public record StudentResponse(
        @Schema(description = "학생 고유 ID", example = "1") Long studentId,
        @Schema(description = "학생 이름", example = "김학생") String name,
        @Schema(description = "학년", example = "초등 3") String grade,
        @Schema(description = "수강반. 미배정이면 null", example = "오후반") String className,
        @Schema(description = "학생 연락처. 미입력이면 null", example = "010-1234-5678") String studentPhone,
        @Schema(description = "보호자 연락처. 미입력이면 null", example = "01012345678") String guardianPhone) {

    public static StudentResponse from(Student student) {
        return new StudentResponse(
                student.getId(),
                student.getName(),
                student.getGrade(),
                student.getClassName(),
                student.getStudentPhone(),
                student.getGuardianPhone());
    }
}
