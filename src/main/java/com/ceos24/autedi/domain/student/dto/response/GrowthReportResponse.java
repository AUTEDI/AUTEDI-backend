package com.ceos24.autedi.domain.student.dto.response;

import com.ceos24.autedi.domain.generation.contract.v1.AiIssueKind;
import com.ceos24.autedi.domain.worksheet.entity.CorrectionArea;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 오류 수가 아닌 교사 확정 피드백 건수다. null은 실제 0건과 구분한다. */
public record GrowthReportResponse(
        Long studentId,
        String period,
        CorrectionArea selectedArea,
        LocalDate from,
        LocalDate to,
        String metricVersion,
        DataStatus dataStatus,
        int minimumPointCount,
        int comparablePointCount,
        int unavailableResultCount,
        List<Point> points,
        List<SeriesStatus> series) {
    public enum DataStatus {
        EMPTY,
        INSUFFICIENT_DATA,
        AVAILABLE
    }

    public enum AggregationStatus {
        AVAILABLE,
        UNREADABLE_SNAPSHOT
    }

    public enum WordCountStatus {
        AVAILABLE,
        MISSING_TRANSCRIPTION,
        EMPTY_TRANSCRIPTION
    }

    public enum AnalysisStatus {
        ANALYZED,
        PARTIAL,
        TEACHER_ONLY,
        NOT_ANALYZED
    }

    public record SeriesStatus(CorrectionArea area, int comparablePointCount, DataStatus dataStatus) {}

    public record Point(
            Long worksheetId,
            String title,
            LocalDateTime completedAt,
            AggregationStatus aggregationStatus,
            Integer wordCount,
            WordCountStatus wordCountStatus,
            Integer errorCount,
            Integer aiErrorCount,
            Integer teacherErrorCount,
            Integer suggestionCount,
            Integer generalCommentCount,
            Integer supplementCommentCount,
            Integer unclassifiedIssueCount,
            List<IssueCount> issueKindCounts,
            List<AreaPoint> areas) {}

    public record AreaPoint(
            CorrectionArea area,
            AnalysisStatus analysisStatus,
            Integer errorCount,
            int aiErrorCount,
            int teacherErrorCount,
            int suggestionCount,
            int unclassifiedIssueCount,
            BigDecimal errorsPer100Words,
            List<IssueCount> issueKindCounts,
            List<FeedbackExample> representativeFeedback) {}

    public record IssueCount(AiIssueKind issueKind, int count) {}

    public record FeedbackExample(String source, Long feedbackId, String originalText, String content) {}
}
