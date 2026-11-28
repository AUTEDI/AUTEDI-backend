package com.ceos24.autedi.domain.student.dto.response;

import com.ceos24.autedi.domain.student.entity.Student;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "학생 목록 항목")
public record StudentSummaryResponse(
        @Schema(description = "학생 고유 ID", example = "1") Long studentId,
        @Schema(description = "학생 이름", example = "김학생") String name,
        @Schema(description = "학년", example = "초등 3") String grade,
        @Schema(description = "반. 미배정이면 null", example = "오후반") String className,
        Long gradeId,
        Long classId) {

    public static StudentSummaryResponse from(Student student) {
        String className = student.getClassName();
        return new StudentSummaryResponse(
                student.getId(),
                student.getName(),
                student.getGrade(),
                className == null || className.isBlank() ? null : className,
                student.getGradeId(),
                student.getClassId());
    }
}
