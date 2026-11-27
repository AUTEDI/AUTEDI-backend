package com.ceos24.autedi.domain.worksheet.service;

import com.ceos24.autedi.domain.worksheet.entity.WorksheetSavedResult;
import com.ceos24.autedi.global.exception.CustomException;
import com.ceos24.autedi.global.exception.ErrorCode;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/** v1 코멘트의 누락된 분류는 GENERAL로 읽으며, 새 저장은 분류를 포함하는 v2다. */
public final class WorksheetSnapshotCodec {
    private static final JsonMapper JSON = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    private WorksheetSnapshotCodec() {}

    public static WorksheetSavedSnapshot decode(WorksheetSavedResult saved) {
        try {
            if (saved.getSchemaVersion() != 1 && saved.getSchemaVersion() != 2)
                throw new IllegalArgumentException("지원하지 않는 저장본 버전");
            return JSON.convertValue(saved.getSnapshot(), WorksheetSavedSnapshot.class);
        } catch (RuntimeException e) {
            throw new CustomException(ErrorCode.SAVED_RESULT_UNREADABLE);
        }
    }
}
