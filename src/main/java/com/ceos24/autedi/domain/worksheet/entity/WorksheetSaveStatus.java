package com.ceos24.autedi.domain.worksheet.entity;

/** 교사의 업무 저장 상태. AI 처리 상태인 WorksheetStatus와 별도로 관리한다. */
public enum WorksheetSaveStatus {
    UNSAVED,
    TEMP_SAVED,
    COMPLETED
}
