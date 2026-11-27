package com.ceos24.autedi.domain.student.service;

import com.ceos24.autedi.domain.comment.entity.CommentFeedbackType;
import com.ceos24.autedi.domain.correction.entity.CorrectionStatus;
import com.ceos24.autedi.domain.correction.entity.FeedbackTaxonomy;
import com.ceos24.autedi.domain.correction.entity.FeedbackType;
import com.ceos24.autedi.domain.generation.contract.v1.AiIssueKind;
import com.ceos24.autedi.domain.student.dto.response.GrowthReportResponse.*;
import com.ceos24.autedi.domain.student.repository.GrowthTranscriptionRow;
import com.ceos24.autedi.domain.worksheet.dto.response.ReviewResponse;
import com.ceos24.autedi.domain.worksheet.entity.CorrectionArea;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** 가변 엔티티를 조회하지 않고 최종 저장된 공개 피드백만 카드 단위로 집계한다. */
final class GrowthReportCalculator {
    private GrowthReportCalculator() {}

    static Point calculate(
            ReviewResponse review,
            Long sourceGenerationId,
            String fileHash,
            List<GrowthTranscriptionRow> transcriptions,
            CorrectionArea selectedArea) {
        var metadata = review.worksheet();
        var pageIds = metadata.pages().stream().map(p -> p.id()).collect(java.util.stream.Collectors.toSet());
        if (pageIds.isEmpty()
                || pageIds.contains(null)
                || pageIds.size() != metadata.pages().size()) throw new IllegalArgumentException("저장본 페이지 정보 불일치");
        Map<CorrectionArea, Bucket> buckets = new EnumMap<>(CorrectionArea.class);
        FeedbackTaxonomy.AREAS.forEach(area -> buckets.put(area, new Bucket()));
        if (review.summary() != null)
            for (var summary : review.summary()) {
                if (summary.area() != null
                        && summary.level() != null
                        && summary.maximumCount() > 0
                        && Objects.equals(sourceGenerationId, summary.generationId())
                        && pageIds.contains(summary.pageId())) {
                    var area = FeedbackTaxonomy.canonical(
                            CorrectionArea.valueOf(summary.area().name()));
                    buckets.get(area).analyzedPages.add(summary.pageId());
                }
            }
        Set<Long> correctionIds = new HashSet<>();
        for (var card : Objects.requireNonNull(review.corrections())) {
            if (card.id() == null
                    || !correctionIds.add(card.id())
                    || card.area() == null
                    || card.feedbackType() == null) throw new IllegalArgumentException("저장본 첨삭 분류 불일치");
            if (card.status() != CorrectionStatus.ACTIVE || !Boolean.TRUE.equals(card.isApplied())) continue;
            var bucket = buckets.get(FeedbackTaxonomy.canonical(card.area()));
            bucket.hasAiFeedback = true;
            if (card.feedbackType() == FeedbackType.SUGGESTION) {
                bucket.suggestions++;
                continue;
            }
            bucket.aiErrors++;
            bucket.addKinds(card.issueKinds());
            bucket.example(new FeedbackExample(card.source(), card.id(), card.originalText(), card.content()));
        }
        int general = 0, supplement = 0;
        Set<Long> commentIds = new HashSet<>();
        for (var comment : Objects.requireNonNull(review.comments())) {
            if (comment.id() == null || !commentIds.add(comment.id())) throw new IllegalArgumentException("중복 코멘트");
            var classification = comment.classification();
            if (classification.type() == CommentFeedbackType.GENERAL) {
                general++;
            } else if (classification.type() == CommentFeedbackType.SUPPLEMENT) {
                supplement++;
            } else {
                var bucket = buckets.get(classification.area());
                bucket.hasTeacherFeedback = true;
                if (classification.type() == CommentFeedbackType.SUGGESTION) bucket.suggestions++;
                else {
                    bucket.teacherErrors++;
                    bucket.addKinds(classification.issueKinds());
                    bucket.example(new FeedbackExample("TEACHER", comment.id(), null, comment.content()));
                }
            }
        }
        WordCount words = words(review, sourceGenerationId, fileHash, pageIds, transcriptions);
        List<AreaPoint> areas = FeedbackTaxonomy.AREAS.stream()
                .filter(area -> selectedArea == null || area == selectedArea)
                .map(area -> buckets.get(area).point(area, pageIds.size(), words))
                .toList();
        int ai = buckets.values().stream().mapToInt(b -> b.aiErrors).sum();
        int teacher = buckets.values().stream().mapToInt(b -> b.teacherErrors).sum();
        List<IssueCount> kinds = FeedbackTaxonomy.ISSUE_KINDS.stream()
                .map(kind -> new IssueCount(
                        kind,
                        buckets.values().stream()
                                .mapToInt(b -> b.kinds.getOrDefault(kind, 0))
                                .sum()))
                .toList();
        return new Point(
                metadata.id(),
                metadata.title(),
                metadata.completedAt(),
                AggregationStatus.AVAILABLE,
                words.count,
                words.status,
                ai + teacher,
                ai,
                teacher,
                buckets.values().stream().mapToInt(b -> b.suggestions).sum(),
                general,
                supplement,
                buckets.values().stream().mapToInt(b -> b.unclassified).sum(),
                kinds,
                areas);
    }

    static Point unreadable(Long worksheetId, String title, LocalDateTime completedAt) {
        return new Point(
                worksheetId,
                title,
                completedAt,
                AggregationStatus.UNREADABLE_SNAPSHOT,
                null,
                WordCountStatus.MISSING_TRANSCRIPTION,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                List.of());
    }

    private static WordCount words(
            ReviewResponse review,
            Long generationId,
            String fileHash,
            Set<Long> pageIds,
            List<GrowthTranscriptionRow> rows) {
        var references = review.aiResults();
        if (generationId == null || references == null || references.size() != pageIds.size())
            return WordCount.missing();
        Set<Long> countedPages = new HashSet<>();
        int count = 0;
        for (var reference : references) {
            if (!Objects.equals(generationId, reference.generationId())
                    || !pageIds.contains(reference.pageId())
                    || !countedPages.add(reference.pageId())) return WordCount.missing();
            var matching = rows.stream()
                    .filter(row -> Objects.equals(row.generationId(), generationId)
                            && Objects.equals(
                                    row.worksheetId(), review.worksheet().id())
                            && Objects.equals(row.pageId(), reference.pageId())
                            && Objects.equals(row.transcriptionId(), reference.transcriptionId())
                            && Objects.equals(row.sourceFileHash(), fileHash))
                    .toList();
            if (matching.size() != 1 || matching.getFirst().text() == null) return WordCount.missing();
            count = Math.addExact(
                    count, GrowthWordCounter.count(matching.getFirst().text()));
        }
        return new WordCount(count, count == 0 ? WordCountStatus.EMPTY_TRANSCRIPTION : WordCountStatus.AVAILABLE);
    }

    private record WordCount(Integer count, WordCountStatus status) {
        static WordCount missing() {
            return new WordCount(null, WordCountStatus.MISSING_TRANSCRIPTION);
        }
    }

    private static final class Bucket {
        private final Set<Long> analyzedPages = new HashSet<>();
        private final Map<AiIssueKind, Integer> kinds = new EnumMap<>(AiIssueKind.class);
        private final List<FeedbackExample> examples = new ArrayList<>();
        private int aiErrors, teacherErrors, suggestions, unclassified;
        private boolean hasAiFeedback, hasTeacherFeedback;

        void addKinds(List<AiIssueKind> values) {
            if (values == null || values.isEmpty()) {
                unclassified++;
                return;
            }
            if (!FeedbackTaxonomy.validKinds(values)) throw new IllegalArgumentException("일반 오류 분류 불일치");
            values.forEach(kind -> kinds.merge(kind, 1, Integer::sum));
        }

        void example(FeedbackExample value) {
            if (examples.size() < 3) examples.add(value);
        }

        AreaPoint point(CorrectionArea area, int pageCount, WordCount words) {
            AnalysisStatus status = analyzedPages.size() == pageCount
                    ? AnalysisStatus.ANALYZED
                    : !analyzedPages.isEmpty() || hasAiFeedback
                            ? AnalysisStatus.PARTIAL
                            : hasTeacherFeedback ? AnalysisStatus.TEACHER_ONLY : AnalysisStatus.NOT_ANALYZED;
            Integer errors = status == AnalysisStatus.NOT_ANALYZED ? null : aiErrors + teacherErrors;
            BigDecimal rate = status == AnalysisStatus.ANALYZED && words.status == WordCountStatus.AVAILABLE
                    ? BigDecimal.valueOf(aiErrors + teacherErrors)
                            .multiply(BigDecimal.valueOf(100))
                            .divide(BigDecimal.valueOf(words.count), 2, RoundingMode.HALF_UP)
                    : null;
            return new AreaPoint(
                    area,
                    status,
                    errors,
                    aiErrors,
                    teacherErrors,
                    suggestions,
                    unclassified,
                    rate,
                    FeedbackTaxonomy.ISSUE_KINDS.stream()
                            .map(kind -> new IssueCount(kind, kinds.getOrDefault(kind, 0)))
                            .toList(),
                    List.copyOf(examples));
        }
    }
}
