package com.ceos24.autedi.domain.generation.contract.v1.validation;

import com.ceos24.autedi.domain.generation.contract.v1.AiAction;
import com.ceos24.autedi.domain.generation.contract.v1.AiAnchor;
import com.ceos24.autedi.domain.generation.contract.v1.AiArea;
import com.ceos24.autedi.domain.generation.contract.v1.AiCorrectionItem;
import com.ceos24.autedi.domain.generation.contract.v1.AiCorrectionResponse;
import com.ceos24.autedi.domain.generation.contract.v1.AiIssueKind;
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

/** DB나 모델 호출 없이 한 페이지 응답을 검증한다. 원문·원본 후보를 수정하거나 제안을 적용하지 않는다. */
public final class AiCorrectionResponseValidator {
    private static final Pattern ENGLISH_WORD = Pattern.compile("[A-Za-z0-9]+(?:['’-][A-Za-z0-9]+)*");

    public AiValidationResult validate(AiCorrectionResponse response, AiValidationContext context) {
        Objects.requireNonNull(response, "response");
        Objects.requireNonNull(context, "context");
        List<Candidate> candidates = new ArrayList<>();
        for (int index = 0; index < response.items().size(); index++) {
            Candidate candidate = new Candidate(index, response.items().get(index));
            checkFields(candidate);
            checkIssuesAndSettings(candidate, context);
            resolveAnchor(candidate, context.transcriptionLines());
            checkBoxes(candidate);
            candidates.add(candidate);
        }
        applyLimits(candidates, context.selectedAreas());
        markOverlaps(candidates);
        List<AiValidatedItem> checked = candidates.stream()
                .map(candidate -> candidate.freeze(context.pageNumber()))
                .toList();
        List<AiAreaSummary> summary = Arrays.stream(AiArea.values())
                .map(area -> summarize(area, checked, context.selectedAreas().get(area)))
                .toList();
        return new AiValidationResult(response, context, checked, summary);
    }

    private static void checkFields(Candidate candidate) {
        AiCorrectionItem item = candidate.original;
        if (item.reason() == null || item.reason().isBlank()) {
            candidate.review(AiValidationCode.INVALID_FIELD_COMBINATION, "reason", "설명 또는 확인 사유가 필요합니다.");
        }
        if (item.action() == null) {
            candidate.review(AiValidationCode.INVALID_FIELD_COMBINATION, "action", "동작이 필요합니다.");
            return;
        }
        switch (item.action()) {
            case REPLACE -> {
                if (item.feedbackType() == null
                        || item.area() == null
                        || item.anchor() == null
                        || item.replacement() == null
                        || item.guidance() != null) {
                    candidate.review(
                            AiValidationCode.INVALID_FIELD_COMBINATION,
                            "action",
                            "REPLACE는 유형·영역·anchor·replacement가 필요하며 guidance는 null이어야 합니다.");
                }
            }
            case GUIDANCE -> {
                if (item.feedbackType() == null
                        || item.area() != AiArea.CONTENT_SUMMARY
                        || item.anchor() == null
                        || item.replacement() != null
                        || item.guidance() == null
                        || item.guidance().isBlank()) {
                    candidate.review(
                            AiValidationCode.INVALID_FIELD_COMBINATION,
                            "action",
                            "GUIDANCE는 유형·CONTENT_SUMMARY·anchor·빈 값이 아닌 guidance가 필요하며 replacement는 null이어야 합니다.");
                }
                if (item.guidance() != null && !item.guidance().isBlank()) checkGuidance(candidate);
            }
            case REVIEW -> {
                candidate.review(AiValidationCode.MODEL_REVIEW, "action", "모델이 교사 확인을 요청한 항목입니다.");
                if (item.feedbackType() != null
                        || item.area() != null
                        || item.replacement() != null
                        || item.guidance() != null
                        || item.anchor() == null && (!item.boxes().isEmpty() || !item.locationUncertain())) {
                    candidate.review(
                            AiValidationCode.INVALID_FIELD_COMBINATION,
                            "action",
                            "REVIEW의 유형·영역·replacement·guidance는 null이며, anchor가 없으면 boxes=[]와 위치 불확실 표시가 필요합니다.");
                }
            }
        }
    }

    private static void checkGuidance(Candidate candidate) {
        String guidance = candidate.original.guidance();
        if (ENGLISH_WORD.matcher(guidance).results().count() > 10) {
            candidate.review(AiValidationCode.GUIDANCE_TOO_LONG, "guidance", "내용 보완 안내는 영어 10단어 이내여야 합니다.");
        }
        boolean hasEnglishLetter = guidance.codePoints().anyMatch(AiCorrectionResponseValidator::isEnglishLetter);
        boolean hasOtherLetters =
                guidance.codePoints().anyMatch(value -> Character.isLetter(value) && !isEnglishLetter(value));
        if (!hasEnglishLetter || hasOtherLetters) {
            candidate.review(
                    AiValidationCode.GUIDANCE_NOT_ENGLISH, "guidance", "내용 보완 안내에 영어 외의 문자 언어가 있거나 영어 단어가 없습니다.");
        }
    }

    private static boolean isEnglishLetter(int value) {
        return value >= 'A' && value <= 'Z' || value >= 'a' && value <= 'z';
    }

    private static void checkIssuesAndSettings(Candidate candidate, AiValidationContext context) {
        AiCorrectionItem item = candidate.original;
        boolean modelReview = item.action() == AiAction.REVIEW;
        Set<AiArea> issueAreas = EnumSet.noneOf(AiArea.class);
        if (item.issueKinds().isEmpty()
                || new LinkedHashSet<>(item.issueKinds()).size()
                        != item.issueKinds().size()) {
            candidate.review(AiValidationCode.INVALID_ISSUE_KINDS, "issue_kinds", "문제 종류는 1개 이상이며 중복할 수 없습니다.");
        }
        for (AiIssueKind kind : item.issueKinds()) {
            AiArea issueArea = areaFor(kind);
            if (modelReview && issueArea != null || !modelReview && issueArea == null) {
                candidate.review(
                        AiValidationCode.INVALID_ISSUE_KINDS, "issue_kinds", "동작과 일반 첨삭·확인 필요 문제 종류가 일치하지 않습니다.");
            }
            if (issueArea != null) issueAreas.add(issueArea);
        }
        if (modelReview || item.area() == null) return;

        if (!issueAreas.contains(item.area())) {
            candidate.review(AiValidationCode.AREA_ISSUE_MISMATCH, "area", "대표 영역이 문제 종류가 속한 영역과 일치하지 않습니다.");
        }
        if (!context.selectedAreas().containsKey(item.area())) {
            candidate.exclude(AiValidationCode.AREA_NOT_SELECTED, "area", "선택하지 않은 대표 영역의 항목입니다.");
        }
        for (AiArea issueArea : issueAreas) {
            if (!context.selectedAreas().containsKey(issueArea)) {
                candidate.exclude(
                        AiValidationCode.ISSUE_AREA_NOT_SELECTED,
                        "issue_kinds",
                        "선택하지 않은 " + issueArea + " 영역의 문제를 다른 대표 영역으로 우회할 수 없습니다.");
            }
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

    private static void resolveAnchor(Candidate candidate, List<String> lines) {
        AiAnchor anchor = candidate.original.anchor();
        if (anchor == null) return;
        int start = anchor.lineId() >= 1 && anchor.lineId() <= lines.size()
                ? AiLocationChecks.findOccurrence(
                        lines.get(anchor.lineId() - 1), anchor.original(), anchor.occurrence())
                : -1;
        if (start < 0) {
            candidate.review(AiValidationCode.ANCHOR_NOT_FOUND, "anchor", "원문 anchor가 전사문의 지정 줄·등장 순서와 일치하지 않습니다.");
        } else {
            candidate.anchor = new AiResolvedAnchor(
                    anchor.lineId(), start, start + anchor.original().length());
        }
    }

    private static void checkBoxes(Candidate candidate) {
        AiCorrectionItem item = candidate.original;
        for (int index = 0; index < item.boxes().size(); index++) {
            try {
                candidate.boxes.add(AiLocationChecks.parseBox(item.boxes().get(index)));
            } catch (IllegalStateException e) {
                candidate.warn(
                        AiValidationCode.INVALID_BOX, "boxes[" + index + "]", "잘못된 좌표를 제외했습니다: " + e.getMessage());
            }
        }
        if (candidate.anchor == null && !candidate.boxes.isEmpty()) {
            candidate.boxes.clear();
            candidate.warn(AiValidationCode.BOX_WITHOUT_ANCHOR, "boxes", "원문 위치가 확인되지 않아 표시 좌표를 제공하지 않습니다.");
        }
        if (candidate.anchor != null && candidate.boxes.isEmpty()) {
            candidate.warn(AiValidationCode.NO_VALID_BOXES, "boxes", "표시할 유효 좌표가 없습니다. 원문 카드와 제안은 보존합니다.");
        }
        if (item.locationUncertain()) {
            candidate.warn(
                    AiValidationCode.MODEL_LOCATION_UNCERTAIN, "location_uncertain", "모델이 이미지 위치를 불확실하게 판단했습니다.");
        } else if (item.boxes().isEmpty()) {
            candidate.warn(
                    AiValidationCode.LOCATION_FLAG_INCONSISTENT,
                    "location_uncertain",
                    "좌표가 없지만 모델의 위치 불확실 표시는 false입니다.");
        }
    }

    private static void applyLimits(List<Candidate> candidates, Map<AiArea, AiCorrectionLevel> selectedAreas) {
        Map<AiArea, Integer> counts = new EnumMap<>(AiArea.class);
        // 검증을 통과한 후보 중 모델 반환 순서를 우선순위로 사용한다. 그 후 화면 목록만 원문 순서로 정렬한다.
        for (Candidate candidate : candidates) {
            if (candidate.disposition() != AiItemDisposition.CORRECTION) continue;
            AiArea area = candidate.original.area();
            AiCorrectionLevel level = selectedAreas.get(area);
            int count = counts.getOrDefault(area, 0);
            if (count >= level.maximumCount()) {
                candidate.exclude(
                        AiValidationCode.CARD_LIMIT_EXCEEDED,
                        "area",
                        "페이지·대표 영역별 최대 " + level.maximumCount() + "개를 초과했습니다.");
            } else {
                counts.put(area, count + 1);
            }
        }
    }

    private static void markOverlaps(List<Candidate> candidates) {
        List<Candidate> visible = candidates.stream()
                .filter(candidate -> candidate.disposition() != AiItemDisposition.EXCLUDED && candidate.anchor != null)
                .toList();
        for (int i = 0; i < visible.size(); i++) {
            for (int j = i + 1; j < visible.size(); j++) {
                Candidate a = visible.get(i), b = visible.get(j);
                if (a.anchor.lineId() == b.anchor.lineId()
                        && a.anchor.start() < b.anchor.end()
                        && b.anchor.start() < a.anchor.end()) {
                    a.warn(AiValidationCode.OVERLAPPING_ANCHOR, "anchor", "items[" + b.index + "]와 원문 범위가 겹칩니다.");
                    b.warn(AiValidationCode.OVERLAPPING_ANCHOR, "anchor", "items[" + a.index + "]와 원문 범위가 겹칩니다.");
                }
            }
        }
    }

    private static AiAreaSummary summarize(AiArea area, List<AiValidatedItem> items, AiCorrectionLevel level) {
        int displayed = (int) items.stream()
                .filter(item -> item.original().area() == area && item.disposition() == AiItemDisposition.CORRECTION)
                .count();
        int omitted = (int) items.stream()
                .filter(item -> item.original().area() == area && item.disposition() == AiItemDisposition.EXCLUDED)
                .count();
        return new AiAreaSummary(
                area,
                level,
                level == null ? 0 : level.recommendedCount(),
                level == null ? 0 : level.maximumCount(),
                displayed,
                omitted);
    }

    private static final class Candidate {
        private final int index;
        private final AiCorrectionItem original;
        private final Set<AiValidationIssue> issues = new LinkedHashSet<>();
        private final List<List<Double>> boxes = new ArrayList<>();
        private AiResolvedAnchor anchor;
        private boolean review;
        private boolean excluded;

        private Candidate(int index, AiCorrectionItem original) {
            this.index = index;
            this.original = original;
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
            return excluded
                    ? AiItemDisposition.EXCLUDED
                    : review ? AiItemDisposition.REVIEW : AiItemDisposition.CORRECTION;
        }

        private AiValidatedItem freeze(int pageNumber) {
            return new AiValidatedItem(pageNumber, index, original, disposition(), anchor, boxes, List.copyOf(issues));
        }
    }
}
