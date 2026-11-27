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

    /** 반복 완료 요청은 기존 저장본을 그대로 반환할 수 있도록 COMPLETED도 허용한다. */
    public Worksheet lockForCompletion(Long userId, Long academyId, Long worksheetId) {
        Worksheet worksheet = locked(academyId, worksheetId);
        requireOwner(worksheet, userId);
        requireReady(worksheet);
        return worksheet;
    }

    /** 파일·설정·생성 등 기존 쓰기의 공통 완료/삭제 차단. AI 단계별 제한은 호출자가 검사한다. */
    public Worksheet lockForChange(Long academyId, Long worksheetId) {
        Worksheet worksheet = locked(academyId, worksheetId);
        requireNotCompleted(worksheet);
        return worksheet;
    }

    public Worksheet readForOwner(Long userId, Long academyId, Long worksheetId) {
        Worksheet worksheet = worksheets
                .findByIdAndAcademyId(worksheetId, academyId)
                .filter(w -> w.getDeletedAt() == null)
                .orElseThrow(() -> new CustomException(ErrorCode.WORKSHEET_NOT_FOUND));
        requireOwner(worksheet, userId);
        requireReady(worksheet);
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
        requireReady(worksheet);
        requireNotCompleted(worksheet);
    }

    private void requireReady(Worksheet worksheet) {
        if (worksheet.getStatus() != WorksheetStatus.READY) {
            throw new CustomException(ErrorCode.INVALID_WORKSHEET_STATE, "학습지가 READY 상태가 아닙니다.");
        }
    }

    private void requireNotCompleted(Worksheet worksheet) {
        if (worksheet.getSaveStatus() == WorksheetSaveStatus.COMPLETED) {
            throw new CustomException(ErrorCode.INVALID_WORKSHEET_STATE, "최종 저장된 학습지는 수정할 수 없습니다.");
        }
    }
}
