package com.ceos24.autedi.domain.student.dto.response;

import com.ceos24.autedi.domain.student.entity.Student;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "같은 기관에서 이름이 같은 기존 학생 후보. 연락처 원문은 포함하지 않습니다.")
public record StudentDuplicateCandidateResponse(
        @Schema(description = "기존 학생 고유 ID", example = "1") Long studentId,
        @Schema(description = "기존 학생 이름", example = "김학생") String name,
        @Schema(description = "기존 학생 학년", example = "초등 3") String grade,
        @Schema(description = "기존 학생 반. 미배정이면 null", example = "오후반") String className,
        @Schema(description = "입력한 학생 연락처와 기존 학생 연락처가 하이픈을 제외하고 일치하는지") boolean studentPhoneMatched,
        @Schema(description = "입력한 보호자 연락처와 기존 보호자 연락처가 하이픈을 제외하고 일치하는지") boolean guardianPhoneMatched,
        Long gradeId,
        Long classId) {

    public static StudentDuplicateCandidateResponse from(Student student, String studentPhone, String guardianPhone) {
        return new StudentDuplicateCandidateResponse(
                student.getId(),
                student.getName(),
                student.getGrade(),
                student.getClassName(),
                matchesPhone(studentPhone, student.getStudentPhone()),
                matchesPhone(guardianPhone, student.getGuardianPhone()),
                student.getGradeId(),
                student.getClassId());
    }

    private static boolean matchesPhone(String requested, String stored) {
        return requested != null
                && !requested.isBlank()
                && stored != null
                && !stored.isBlank()
                && requested.strip().replace("-", "").equals(stored.strip().replace("-", ""));
    }
}
