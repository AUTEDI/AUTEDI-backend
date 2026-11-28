package com.ceos24.autedi.domain.insight.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record StudentInsightResponse(
        Long studentId,
        Status status,
        boolean isStale,
        boolean reused,
        int candidateResultCount,
        Integer usedResultCount,
        int minimumResultCount,
        LocalDate analysisFrom,
        LocalDate analysisTo,
        LocalDateTime generatedAt,
        List<InsightDocument.Item> insights,
        List<InsightDocument.CoachingPoint> coachingPoints,
        List<InsightDocument.Evidence> evidence) {
    public enum Status {
        EMPTY,
        INSUFFICIENT_DATA,
        NOT_GENERATED,
        NO_FEEDBACK,
        READY
    }
}
