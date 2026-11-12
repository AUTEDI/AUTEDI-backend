package com.ceos24.autedi.domain.generation.contract.v1_1.validation;

import com.ceos24.autedi.domain.generation.contract.v1_1.AiAction;
import com.ceos24.autedi.domain.generation.contract.v1_1.AiArea;
import com.ceos24.autedi.domain.generation.contract.v1_1.AiCorrectionItem;
import com.ceos24.autedi.domain.generation.contract.v1_1.AiCorrectionResponse;
import com.ceos24.autedi.domain.generation.contract.v1_1.AiEditOperation;
import com.ceos24.autedi.domain.generation.contract.v1_1.AiFeedbackType;
import com.ceos24.autedi.domain.generation.contract.v1_1.AiIssueKind;
import com.ceos24.autedi.domain.generation.contract.v1_1.AiRevisionScope;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/** v1.1 한 페이지의 독립 검증 진입점. 실제 AI·DB·서비스 v1 경로를 실행하거나 교체하지 않는다. */
public final class AiCorrectionResponseValidator {
    private static final Pattern ENGLISH_WORD = Pattern.compile("[A-Za-z0-9]+(?:['’\u2010\u2011-][A-Za-z0-9]+)*");
    private static final Pattern SOURCE_WORD = Pattern.compile("[\\p{L}\\p{N}]+(?:['’\u2010\u2011-][\\p{L}\\p{N}]+)*");
    private final AiLocationValidator locations = new AiLocationValidator();
    private final AiEditValidator edits = new AiEditValidator();

    public AiValidationResult validate(AiCorrectionResponse response, AiValidationContext context) {
        Objects.requireNonNull(response, "response");
        Objects.requireNonNull(context, "context");
        List<Candidate> candidates = new ArrayList<>();
        for (AiEditValidationResult edit : edits.validate(locations.validate(response, context.location()))) {
            Candidate candidate = new Candidate(edit);
            checkSettings(candidate, context.selectedAreas());
            checkScope(candidate);
            checkGuidance(candidate);
            candidates.add(candidate);
        }
        // 겹침은 일반 카드 후보에서 먼저 찾는다. 한도 때문에 숨겨진 후보와의 충돌은 표시 경고로 만들지 않는다.
        List<Overlap> overlaps = findOverlaps(candidates);
        applyLimits(candidates, context.selectedAreas());
        for (Overlap overlap : overlaps) overlap.warnIfVisible();
        List<AiValidatedItem> checked =
                candidates.stream().map(Candidate::freeze).toList();
        List<AiAreaSummary> summary = Arrays.stream(AiArea.values())
                .map(area -> summarize(area, checked, context.selectedAreas().get(area)))
                .toList();
        return new AiValidationResult(response, context, checked, summary);
    }

    private static void checkSettings(Candidate candidate, Map<AiArea, AiCorrectionLevel> selected) {
        AiCorrectionItem item = candidate.original();
        boolean modelReview = item.action() == AiAction.REVIEW;
        Set<AiArea> issueAreas = EnumSet.noneOf(AiArea.class);
        if (item.issueKinds().isEmpty()
                || new LinkedHashSet<>(item.issueKinds()).size()
                        != item.issueKinds().size()) {
            candidate.review(AiValidationCode.INVALID_ISSUE_KINDS, "issue_kinds", "문제 종류는 1개 이상이며 중복할 수 없습니다.");
        }
        for (AiIssueKind kind : item.issueKinds()) {
            AiArea area = areaFor(kind);
            if (modelReview && area != null || !modelReview && area == null) {
                candidate.review(
                        AiValidationCode.INVALID_ISSUE_KINDS, "issue_kinds", "일반 첨삭과 확인 필요 문제 종류가 동작과 맞지 않습니다.");
            }
            if (area != null) issueAreas.add(area);
        }
        if (modelReview) {
            candidate.review(AiValidationCode.MODEL_REVIEW, "action", "모델이 교사 확인을 요청한 항목입니다.");
            return;
        }
        if (item.area() != null) {
            if (!issueAreas.contains(item.area())) {
                candidate.review(AiValidationCode.AREA_ISSUE_MISMATCH, "area", "대표 영역이 문제 종류에 포함되지 않습니다.");
            }
            if (!selected.containsKey(item.area())) {
                candidate.exclude(AiValidationCode.AREA_NOT_SELECTED, "area", "선택하지 않은 대표 영역입니다.");
            }
        }
        for (AiArea area : issueAreas) {
            if (!selected.containsKey(area)) {
                candidate.exclude(
                        AiValidationCode.ISSUE_AREA_NOT_SELECTED, "issue_kinds", "선택하지 않은 " + area + " 영역의 수정을 포함합니다.");
            }
        }
        if (item.feedbackType() == AiFeedbackType.SUGGESTION
                && (item.area() != null && selected.get(item.area()) == AiCorrectionLevel.STANDARD
                        || issueAreas.stream().anyMatch(area -> selected.get(area) == AiCorrectionLevel.STANDARD))) {
            candidate.exclude(
                    AiValidationCode.STANDARD_SUGGESTION, "feedback_type", "Standard 영역에는 선택적 개선 제안을 포함할 수 없습니다.");
        }
    }

    private static AiArea areaFor(AiIssueKind kind) {
        return switch (kind) {
            case GRAMMAR, SPELLING, PUNCTUATION -> AiArea.GRAMMAR_SPELLING;
            case VOCABULARY -> AiArea.VOCABULARY_EXPRESSION;
            case SENTENCE_STRUCTURE -> AiArea.SENTENCE_STRUCTURE;
            case CONTENT -> AiArea.CONTENT_SUMMARY;
            case OCR_MISMATCH, ILLEGIBLE, UNCLEAR_INTENT, IMAGE_PROBLEM -> null;
        };
    }

    private static void checkScope(Candidate candidate) {
        AiCorrectionItem item = candidate.original();
        if (item.action() != AiAction.REPLACE
                || item.area() == null
                || item.revisionScope() == null
                || item.edit() == null) return;
        AiRevisionScope scope = item.revisionScope();
        AiEditOperation operation = item.edit().operation();
        boolean sentence = scope == AiRevisionScope.SENTENCE || scope == AiRevisionScope.SENTENCE_GROUP;
        if (scope == AiRevisionScope.WORD
                && candidate.edit.location().sourceResolved()
                && SOURCE_WORD
                                .matcher(candidate.edit.location().originalText())
                                .results()
                                .limit(2)
                                .count()
                        > 1) {
            candidate.review(AiValidationCode.SCOPE_TEXT_MISMATCH, "revision_scope", "여러 단어의 원문 범위를 WORD로 신고할 수 없습니다.");
        }
        if (item.area() != AiArea.SENTENCE_STRUCTURE
                && scope != AiRevisionScope.WORD
                && scope != AiRevisionScope.PHRASE) {
            candidate.review(
                    AiValidationCode.AREA_SCOPE_VIOLATION, "revision_scope", "이 영역의 원문 수정 범위는 WORD 또는 PHRASE여야 합니다.");
        }
        if (operation == AiEditOperation.RESTRUCTURE && (item.area() != AiArea.SENTENCE_STRUCTURE || !sentence)
                || operation == AiEditOperation.REPLACE && sentence) {
            candidate.review(
                    AiValidationCode.SCOPE_OPERATION_MISMATCH,
                    "edit.operation",
                    "문장 재구성은 SENTENCE_STRUCTURE의 SENTENCE 또는 SENTENCE_GROUP이어야 하며 문장 전체 교체는 RESTRUCTURE로 표현합니다.");
        }
    }

    private static void checkGuidance(Candidate candidate) {
        AiCorrectionItem item = candidate.original();
        if (item.action() != AiAction.GUIDANCE
                || item.guidance() == null
                || item.guidance().isBlank()) return;
        if (ENGLISH_WORD.matcher(item.guidance()).results().count() > 10) {
            candidate.review(AiValidationCode.GUIDANCE_TOO_LONG, "guidance", "내용 보완 안내는 영어 10단어 이내여야 합니다.");
        }
        boolean english = item.guidance().codePoints().anyMatch(AiCorrectionResponseValidator::isEnglishLetter);
        boolean other = item.guidance()
                .codePoints()
                .anyMatch(value -> (Character.isLetter(value) || Character.getType(value) == Character.NON_SPACING_MARK)
                        && !isEnglishLetter(value));
        if (!english || other) {
            candidate.review(
                    AiValidationCode.GUIDANCE_NOT_ENGLISH, "guidance", "안내에는 영어 단어가 필요하며 다른 문자 언어를 섞을 수 없습니다.");
        }
    }

    private static boolean isEnglishLetter(int value) {
        return value >= 'A' && value <= 'Z' || value >= 'a' && value <= 'z';
    }

    private static void applyLimits(List<Candidate> candidates, Map<AiArea, AiCorrectionLevel> selected) {
        Map<AiArea, Integer> counts = new EnumMap<>(AiArea.class);
        for (AiFeedbackType type : List.of(AiFeedbackType.CORRECTION, AiFeedbackType.SUGGESTION)) {
            for (Candidate candidate : candidates) {
                if (candidate.disposition() != AiItemDisposition.CARD
                        || candidate.original().feedbackType() != type) continue;
                AiArea area = candidate.original().area();
                int count = counts.getOrDefault(area, 0);
                if (count >= selected.get(area).maximumCount()) {
                    candidate.exclude(AiValidationCode.CARD_LIMIT_EXCEEDED, "area", "페이지·대표 영역별 최대 7개를 초과했습니다.");
                } else {
                    counts.put(area, count + 1);
                }
            }
        }
    }

    private static List<Overlap> findOverlaps(List<Candidate> candidates) {
        List<Candidate> eligible = candidates.stream()
                .filter(c -> c.disposition() == AiItemDisposition.CARD)
                .toList();
        List<Overlap> overlaps = new ArrayList<>();
        for (int i = 0; i < eligible.size(); i++) {
            for (int j = i + 1; j < eligible.size(); j++) {
                Candidate a = eligible.get(i), b = eligible.get(j);
                boolean overlap = a.edit.location().resolvedAnchors().stream()
                        .anyMatch(first -> b.edit.location().resolvedAnchors().stream()
                                .anyMatch(second -> first.lineId() == second.lineId()
                                        && first.start() < second.end()
                                        && second.start() < first.end()));
                if (overlap) overlaps.add(new Overlap(a, b));
            }
        }
        return overlaps;
    }

    private static AiAreaSummary summarize(AiArea area, List<AiValidatedItem> checked, AiCorrectionLevel level) {
        int cards = 0, omitted = 0;
        for (AiValidatedItem item : checked) {
            if (item.original().action() == AiAction.REVIEW || item.original().area() != area) continue;
            if (item.disposition() == AiItemDisposition.CARD) cards++;
            else omitted++;
        }
        return new AiAreaSummary(area, level, level == null ? 0 : level.maximumCount(), cards, cards, omitted);
    }

    private record Overlap(Candidate first, Candidate second) {
        private void warnIfVisible() {
            if (first.disposition() != AiItemDisposition.CARD || second.disposition() != AiItemDisposition.CARD) return;
            first.warn(
                    AiValidationCode.OVERLAPPING_ANCHORS,
                    "anchors",
                    "items[" + second.edit.location().candidateIndex() + "]와 원문 범위가 겹칩니다.");
            second.warn(
                    AiValidationCode.OVERLAPPING_ANCHORS,
                    "anchors",
                    "items[" + first.edit.location().candidateIndex() + "]와 원문 범위가 겹칩니다.");
        }
    }

    private static final class Candidate {
        private final AiEditValidationResult edit;
        private final List<AiValidationIssue> issues = new ArrayList<>();
        private boolean review;
        private boolean excluded;

        private Candidate(AiEditValidationResult edit) {
            this.edit = edit;
            review = edit.status() != AiEditValidationStatus.CONSISTENT
                    || edit.location().original().action() == AiAction.REVIEW;
        }

        private AiCorrectionItem original() {
            return edit.location().original();
        }

        private void warn(AiValidationCode code, String field, String message) {
            issues.add(new AiValidationIssue(code, field, message));
        }

        private void review(AiValidationCode code, String field, String message) {
            review = true;
            warn(code, field, message);
        }

        private void exclude(AiValidationCode code, String field, String message) {
            excluded = true;
            warn(code, field, message);
        }

        private AiItemDisposition disposition() {
            return excluded ? AiItemDisposition.EXCLUDED : review ? AiItemDisposition.REVIEW : AiItemDisposition.CARD;
        }

        private AiValidatedItem freeze() {
            return new AiValidatedItem(edit, disposition(), issues);
        }
    }
}
