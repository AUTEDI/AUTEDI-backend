package com.ceos24.autedi.domain.insight.dto;

import com.ceos24.autedi.domain.correction.entity.FeedbackType;
import com.ceos24.autedi.domain.worksheet.entity.CorrectionArea;
import java.time.LocalDateTime;
import java.util.List;

/** 화면의 인사이트 목록과 다음 수업 추천 포인트. 근거는 서버가 제공한 ID만 사용한다. */
public record InsightDocument(List<Item> insights, List<CoachingPoint> coachingPoints, List<Evidence> evidence) {
    public enum Kind {
        ERROR_PATTERN,
        IMPROVEMENT
    }

    public record Item(String title, String description, CorrectionArea area, Kind kind, List<String> evidenceIds) {}

    public record CoachingPoint(String content, List<String> evidenceIds) {}

    public record Evidence(
            String id,
            Long worksheetId,
            Long feedbackId,
            String source,
            FeedbackType feedbackType,
            CorrectionArea area,
            LocalDateTime completedAt,
            String originalText,
            String content,
            String explanation) {}
}
