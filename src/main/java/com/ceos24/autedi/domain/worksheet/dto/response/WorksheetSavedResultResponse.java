package com.ceos24.autedi.domain.worksheet.dto.response;

import java.time.LocalDateTime;

/** review는 저장 당시의 읽기 전용 표현이다. 편집에는 resume 응답의 최신 ETag를 사용한다. */
public record WorksheetSavedResultResponse(
        Long worksheetId, long revision, String etag, LocalDateTime savedAt, Long savedBy, ReviewResponse review) {}
