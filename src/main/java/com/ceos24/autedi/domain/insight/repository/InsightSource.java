package com.ceos24.autedi.domain.insight.repository;

import java.time.LocalDateTime;

/** JSON을 읽지 않는 최대 10개 후보 메타데이터. 캐시 무효화와 근거 추적에 사용한다. */
public record InsightSource(
        Long worksheetId,
        long revision,
        int schemaVersion,
        String sourceFileHash,
        Long generationId,
        LocalDateTime completedAt) {}
