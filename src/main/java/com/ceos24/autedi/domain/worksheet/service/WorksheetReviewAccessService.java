package com.ceos24.autedi.domain.worksheet.service;

import com.ceos24.autedi.domain.worksheet.entity.Worksheet;
import com.ceos24.autedi.domain.worksheet.entity.WorksheetSaveStatus;
import com.ceos24.autedi.domain.worksheet.entity.WorksheetStatus;
import com.ceos24.autedi.domain.worksheet.policy.WorksheetAccessPolicy;
import com.ceos24.autedi.global.exception.CustomException;
import com.ceos24.autedi.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** A의 공통 삭제·완료 정책 위에 B의 READY·업로더 규칙을 적용한다. 쓰기 호출자는 트랜잭션을 연다. */
@Service
@RequiredArgsConstructor
public class WorksheetReviewAccessService {
    private final WorksheetAccessPolicy worksheetAccessPolicy;

    public Worksheet lockForEdit(Long userId, Long academyId, Long worksheetId) {
        Worksheet worksheet = worksheetAccessPolicy.getForWrite(academyId, worksheetId);
        if (worksheet.getSaveStatus() == WorksheetSaveStatus.TEMP_SAVED) requireOwner(worksheet, userId);
        requireReady(worksheet);
        return worksheet;
    }

    public Worksheet lockForOwner(Long userId, Long academyId, Long worksheetId) {
        Worksheet worksheet = worksheetAccessPolicy.getForWrite(academyId, worksheetId);
        requireOwner(worksheet, userId);
        requireReady(worksheet);
        return worksheet;
    }

    /** 반복 완료 요청은 기존 저장본을 그대로 반환할 수 있도록 COMPLETED도 허용한다. */
    public Worksheet lockForCompletion(Long userId, Long academyId, Long worksheetId) {
        Worksheet worksheet = worksheetAccessPolicy.getLockedReadable(academyId, worksheetId);
        requireOwner(worksheet, userId);
        requireReady(worksheet);
        return worksheet;
    }

    public Worksheet readForOwner(Long userId, Long academyId, Long worksheetId) {
        Worksheet worksheet = worksheetAccessPolicy.getReadable(academyId, worksheetId);
        requireOwner(worksheet, userId);
        requireReady(worksheet);
        return worksheet;
    }

    private void requireOwner(Worksheet worksheet, Long userId) {
        if (!worksheet.getUser().getId().equals(userId)) throw new CustomException(ErrorCode.ACCESS_DENIED);
    }

    private void requireReady(Worksheet worksheet) {
        if (worksheet.getStatus() != WorksheetStatus.READY) {
            throw new CustomException(ErrorCode.INVALID_WORKSHEET_STATE, "학습지가 READY 상태가 아닙니다.");
        }
    }
}
