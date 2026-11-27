package com.ceos24.autedi.domain.worksheet.repository;

import com.ceos24.autedi.domain.worksheet.entity.WorksheetSaveStatus;
import com.ceos24.autedi.domain.worksheet.entity.WorksheetStatus;
import java.time.LocalDateTime;

/** 목록에 필요한 스칼라 값만 조회한다. 저장본 JSON과 첨삭·코멘트는 읽지 않는다. */
public record SavedWorksheetRow(
        Long worksheetId,
        String title,
        WorksheetSaveStatus saveStatus,
        WorksheetStatus status,
        LocalDateTime savedAt,
        LocalDateTime lastWorkedAt,
        Long studentId,
        String studentName,
        String grade,
        String className,
        Long teacherId,
        String teacherName) {}
