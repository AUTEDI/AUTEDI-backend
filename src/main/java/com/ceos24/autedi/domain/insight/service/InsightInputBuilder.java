package com.ceos24.autedi.domain.insight.service;

import com.ceos24.autedi.domain.comment.entity.CommentFeedbackType;
import com.ceos24.autedi.domain.correction.entity.CorrectionStatus;
import com.ceos24.autedi.domain.correction.entity.FeedbackTaxonomy;
import com.ceos24.autedi.domain.correction.entity.FeedbackType;
import com.ceos24.autedi.domain.insight.dto.InsightDocument.Evidence;
import com.ceos24.autedi.domain.insight.repository.InsightSource;
import com.ceos24.autedi.domain.worksheet.dto.response.ReviewResponse;
import com.ceos24.autedi.domain.worksheet.entity.CorrectionArea;
import com.ceos24.autedi.domain.worksheet.entity.WorksheetSaveStatus;
import com.ceos24.autedi.global.exception.CustomException;
import com.ceos24.autedi.global.exception.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/** 한 번에 한 저장본만 읽고, 도형/이미지/이름/연락처를 제외한 작은 텍스트 패킷만 유지한다. */
@Slf4j
@Component
@RequiredArgsConstructor
public class InsightInputBuilder {
    private static final JsonMapper JSON = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();
    private final StudentInsightStore store;

    public record Result(
            Long worksheetId,
            LocalDateTime completedAt,
            int errorCount,
            int suggestionCount,
            Map<CorrectionArea, Integer> errorsByArea,
            Map<String, Integer> issueKindCounts,
            int unclassifiedIssueCount) {}

    public record Input(List<Result> results, List<Evidence> evidence, boolean excerptsOnly) {
        public String json() {
            String text = JSON.writeValueAsString(this);
            if (text.getBytes(StandardCharsets.UTF_8).length > 96 * 1024)
                throw new CustomException(ErrorCode.INSIGHT_INPUT_TOO_LARGE);
            return text;
        }
    }

    public Input build(Long academyId, Long studentId, List<InsightSource> sources) {
        List<Result> results = new ArrayList<>();
        List<Evidence> evidence = new ArrayList<>();
        for (var source : sources) {
            try {
                var saved = store.snapshot(academyId, studentId, source);
                if (saved.schemaVersion() != 1 && saved.schemaVersion() != 2)
                    throw new IllegalArgumentException("저장본 버전");
                var review = JSON.convertValue(saved.snapshot().get("review"), ReviewResponse.class);
                if (review == null
                        || review.worksheet() == null
                        || !Objects.equals(review.worksheet().id(), source.worksheetId())
                        || review.worksheet().student() == null
                        || !Objects.equals(review.worksheet().student().studentId(), studentId)
                        || review.worksheet().saveStatus() != WorksheetSaveStatus.COMPLETED
                        || !Objects.equals(review.worksheet().completedAt(), source.completedAt())
                        || !Objects.equals(review.worksheet().latestGenerationId(), source.generationId())
                        || review.corrections() == null
                        || review.comments() == null) throw new IllegalArgumentException("저장본 연결");
                var bucket = new Bucket(source);
                for (var card : review.corrections().stream()
                        .sorted(Comparator.comparing(c -> c.id()))
                        .toList()) {
                    if (card.status() != CorrectionStatus.ACTIVE
                            || !Boolean.TRUE.equals(card.isApplied())
                            || "FIXED".equals(card.source())) continue;
                    if (card.feedbackType() == null || card.area() == null) throw new IllegalArgumentException("분류 누락");
                    bucket.add(
                            new Evidence(
                                    "ai:" + source.worksheetId() + ":" + card.id(),
                                    source.worksheetId(),
                                    card.id(),
                                    "AI",
                                    card.feedbackType(),
                                    FeedbackTaxonomy.canonical(card.area()),
                                    source.completedAt(),
                                    clip(card.originalText(), 120),
                                    clip(card.content(), 160),
                                    Boolean.TRUE.equals(card.explanationStale()) ? null : clip(card.explanation(), 80)),
                            card.issueKinds(),
                            false);
                }
                for (var comment : review.comments().stream()
                        .sorted(Comparator.comparing(c -> c.id()))
                        .toList()) {
                    var c = comment.classification();
                    if (c.type() != CommentFeedbackType.CORRECTION && c.type() != CommentFeedbackType.SUGGESTION)
                        continue;
                    bucket.add(
                            new Evidence(
                                    "teacher:" + source.worksheetId() + ":" + comment.id(),
                                    source.worksheetId(),
                                    comment.id(),
                                    "TEACHER",
                                    c.type() == CommentFeedbackType.CORRECTION
                                            ? FeedbackType.CORRECTION
                                            : FeedbackType.SUGGESTION,
                                    c.area(),
                                    source.completedAt(),
                                    null,
                                    clip(comment.content(), 160),
                                    null),
                            c.issueKinds(),
                            true);
                }
                results.add(bucket.result());
                var selected = bucket.selected();
                evidence.addAll(selected);
            } catch (CustomException e) {
                throw e;
            } catch (RuntimeException e) {
                log.warn(
                        "[insight] 분석 저장본 제외: worksheetId={}, studentId={}, reason={}",
                        source.worksheetId(),
                        studentId,
                        e.getClass().getSimpleName());
            }
        }
        results.sort(Comparator.comparing(Result::completedAt).thenComparing(Result::worksheetId));
        return new Input(List.copyOf(results), List.copyOf(evidence), true);
    }

    private static String clip(String text, int max) {
        if (text == null) return null;
        int end = Math.min(text.length(), max);
        if (end < text.length() && end > 0 && Character.isHighSurrogate(text.charAt(end - 1))) end--;
        return text.substring(0, end);
    }

    private static final class Bucket {
        private final InsightSource source;
        private final List<Evidence> ai = new ArrayList<>(),
                teacher = new ArrayList<>(),
                suggestion = new ArrayList<>();
        private final Map<CorrectionArea, Integer> areas = new EnumMap<>(CorrectionArea.class);
        private final Map<String, Integer> kinds = new TreeMap<>();
        private int errors, suggestions, unclassified;

        Bucket(InsightSource source) {
            this.source = source;
            FeedbackTaxonomy.AREAS.forEach(a -> areas.put(a, 0));
        }

        void add(
                Evidence value,
                List<com.ceos24.autedi.domain.generation.contract.v1.AiIssueKind> issueKinds,
                boolean manual) {
            if (value.feedbackType() == FeedbackType.SUGGESTION) {
                suggestions++;
                offer(suggestion, value);
                return;
            }
            errors++;
            areas.merge(value.area(), 1, Integer::sum);
            if (issueKinds == null || issueKinds.isEmpty()) unclassified++;
            else {
                if (!FeedbackTaxonomy.validKinds(issueKinds)) throw new IllegalArgumentException("오류 종류");
                issueKinds.forEach(k -> kinds.merge(k.name().toLowerCase(Locale.ROOT), 1, Integer::sum));
            }
            var target = manual ? teacher : ai;
            offer(target, value);
        }

        Result result() {
            return new Result(
                    source.worksheetId(),
                    source.completedAt(),
                    errors,
                    suggestions,
                    Map.copyOf(areas),
                    Map.copyOf(kinds),
                    unclassified);
        }

        List<Evidence> selected() {
            List<Evidence> out = new ArrayList<>();
            Map<CorrectionArea, List<Evidence>> byArea = new EnumMap<>(CorrectionArea.class);
            for (var area : FeedbackTaxonomy.AREAS) {
                var a = ai.stream().filter(e -> e.area() == area).toList();
                var t = teacher.stream().filter(e -> e.area() == area).toList();
                List<Evidence> values = new ArrayList<>();
                for (int i = 0; i < 2; i++) {
                    if (i < a.size()) values.add(a.get(i));
                    if (i < t.size()) values.add(t.get(i));
                }
                suggestion.stream().filter(e -> e.area() == area).forEach(values::add);
                byArea.put(area, values);
            }
            for (int i = 0; i < 6 && out.size() < 6; i++)
                for (var area : FeedbackTaxonomy.AREAS) {
                    var values = byArea.get(area);
                    if (out.size() < 6 && i < values.size()) out.add(values.get(i));
                }
            return out;
        }

        private void offer(List<Evidence> target, Evidence value) {
            if (target.stream().filter(e -> e.area() == value.area()).count() < 2) target.add(value);
        }
    }
}
