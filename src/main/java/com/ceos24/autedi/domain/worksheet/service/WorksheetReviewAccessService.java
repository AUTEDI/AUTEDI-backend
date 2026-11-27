package com.ceos24.autedi.domain.worksheet.service;

import com.ceos24.autedi.domain.worksheet.entity.Worksheet;
import com.ceos24.autedi.domain.worksheet.entity.WorksheetSaveStatus;
import com.ceos24.autedi.domain.worksheet.entity.WorksheetStatus;
import com.ceos24.autedi.domain.worksheet.repository.WorksheetRepository;
import com.ceos24.autedi.global.exception.CustomException;
import com.ceos24.autedi.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 편집·명시적 저장·복원은 같은 부모 행을 먼저 잠근다. 호출자는 쓰기 트랜잭션이어야 한다. */
@Service
@RequiredArgsConstructor
public class WorksheetReviewAccessService {
    private final WorksheetRepository worksheets;

    public Worksheet lockForEdit(Long userId, Long academyId, Long worksheetId) {
        Worksheet worksheet = locked(academyId, worksheetId);
        if (worksheet.getSaveStatus() == WorksheetSaveStatus.TEMP_SAVED) requireOwner(worksheet, userId);
        requireEditable(worksheet);
        return worksheet;
    }

    public Worksheet lockForOwner(Long userId, Long academyId, Long worksheetId) {
        Worksheet worksheet = locked(academyId, worksheetId);
        requireOwner(worksheet, userId);
        requireEditable(worksheet);
        return worksheet;
    }

    public Worksheet readForOwner(Long userId, Long academyId, Long worksheetId) {
        Worksheet worksheet = worksheets
                .findByIdAndAcademyId(worksheetId, academyId)
                .filter(w -> w.getDeletedAt() == null)
                .orElseThrow(() -> new CustomException(ErrorCode.WORKSHEET_NOT_FOUND));
        requireOwner(worksheet, userId);
        requireEditable(worksheet);
        return worksheet;
    }

    private Worksheet locked(Long academyId, Long worksheetId) {
        return worksheets
                .findLockedByIdAndAcademyId(worksheetId, academyId)
                .filter(w -> w.getDeletedAt() == null)
                .orElseThrow(() -> new CustomException(ErrorCode.WORKSHEET_NOT_FOUND));
    }

    private void requireOwner(Worksheet worksheet, Long userId) {
        if (!worksheet.getUser().getId().equals(userId)) throw new CustomException(ErrorCode.ACCESS_DENIED);
    }

    private void requireEditable(Worksheet worksheet) {
        if (worksheet.getStatus() != WorksheetStatus.READY
                || worksheet.getSaveStatus() == WorksheetSaveStatus.COMPLETED) {
            throw new CustomException(ErrorCode.INVALID_WORKSHEET_STATE, "READY 상태의 미완료 학습지만 사용할 수 있습니다.");
        }
    }
}
