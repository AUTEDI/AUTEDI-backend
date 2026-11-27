package com.ceos24.autedi.domain.teacher.dto.response;

import com.ceos24.autedi.domain.student.entity.Student;
import com.ceos24.autedi.domain.worksheet.entity.Worksheet;
import com.ceos24.autedi.domain.worksheet.entity.WorksheetSaveStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "선생님 첨삭 이력의 한 행. 임시저장과 완료 학습지를 담는다.")
public record TeacherWorksheetHistoryItemResponse(
        @Schema(description = "학습지 고유 ID", example = "31") Long worksheetId,
        @Schema(description = "학습지 제목. 지정하지 않았으면 null", example = "11월 영어 작문", nullable = true) String title,
        @Schema(description = "학생 ID. 지정하지 않았으면 null", example = "17", nullable = true) Long studentId,
        @Schema(description = "학생 이름. 지정하지 않았으면 null", example = "김학생", nullable = true) String studentName,
        @Schema(description = "업무 저장 상태(TEMP_SAVED 임시저장, COMPLETED 완료)", example = "TEMP_SAVED")
                WorksheetSaveStatus saveStatus,
        @Schema(description = "처음 저장한 시각. 월 집계의 기준") LocalDateTime firstSavedAt,
        @Schema(description = "마지막으로 저장한 시각", nullable = true) LocalDateTime lastSavedAt) {

    public static TeacherWorksheetHistoryItemResponse from(Worksheet worksheet) {
        Student student = worksheet.getStudent();
        return new TeacherWorksheetHistoryItemResponse(
                worksheet.getId(),
                worksheet.getTitle(),
                student == null ? null : student.getId(),
                student == null ? null : student.getName(),
                worksheet.getSaveStatus(),
                worksheet.getFirstSavedAt(),
                worksheet.getLastSavedAt());
    }
}
