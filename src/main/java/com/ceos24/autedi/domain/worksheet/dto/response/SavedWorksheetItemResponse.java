package com.ceos24.autedi.domain.worksheet.dto.response;

import com.ceos24.autedi.domain.student.dto.response.StudentSummaryResponse;
import com.ceos24.autedi.domain.worksheet.entity.WorksheetSaveStatus;
import com.ceos24.autedi.domain.worksheet.entity.WorksheetStatus;
import com.ceos24.autedi.domain.worksheet.repository.SavedWorksheetRow;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "저장 결과물 목록의 한 행. 학생·교사의 연락처와 저장본 JSON은 포함하지 않습니다.")
public record SavedWorksheetItemResponse(
        Long worksheetId,
        String title,
        WorksheetSaveStatus saveStatus,
        @Schema(description = "최근 명시적 저장 시각") LocalDateTime savedAt,
        @Schema(description = "추가 편집을 포함한 최근 작업 시각") LocalDateTime lastWorkedAt,
        StudentSummaryResponse student,
        TeacherSummary teacher,
        @Schema(description = "READY 상태여서 저장본 조회 가능한지. 실제 조회에서 원본·저장본 검증을 추가 수행") boolean canView,
        @Schema(description = "READY 임시저장이며 업로더 본인이어서 명시적 이어쓰기가 가능한지") boolean canResume) {
    public record TeacherSummary(Long teacherId, String name) {}

    public static SavedWorksheetItemResponse from(SavedWorksheetRow row, Long userId) {
        boolean ready = row.status() == WorksheetStatus.READY;
        String className = row.className();
        return new SavedWorksheetItemResponse(
                row.worksheetId(),
                row.title(),
                row.saveStatus(),
                row.savedAt(),
                row.lastWorkedAt(),
                new StudentSummaryResponse(
                        row.studentId(),
                        row.studentName(),
                        row.grade(),
                        className == null || className.isBlank() ? null : className),
                new TeacherSummary(row.teacherId(), row.teacherName()),
                ready,
                ready
                        && row.saveStatus() == WorksheetSaveStatus.TEMP_SAVED
                        && row.teacherId().equals(userId));
    }
}
