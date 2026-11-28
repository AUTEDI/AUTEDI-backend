package com.ceos24.autedi.domain.correction.entity;

import com.ceos24.autedi.domain.generation.contract.v1.AiIssueKind;
import com.ceos24.autedi.domain.worksheet.entity.CorrectionArea;
import java.util.List;
import java.util.Set;

/** 정상 피드백의 분류. OCR·이미지 진단 항목은 학생 오류에 포함하지 않는다. */
public final class FeedbackTaxonomy {
    public static final List<AiIssueKind> ISSUE_KINDS = List.of(
            AiIssueKind.GRAMMAR,
            AiIssueKind.SPELLING,
            AiIssueKind.PUNCTUATION,
            AiIssueKind.VOCABULARY,
            AiIssueKind.SENTENCE_STRUCTURE,
            AiIssueKind.CONTENT);
    public static final List<CorrectionArea> AREAS = List.of(
            CorrectionArea.GRAMMAR_SPELLING, CorrectionArea.VOCABULARY_EXPRESSION,
            CorrectionArea.SENTENCE_STRUCTURE, CorrectionArea.CONTENT_SUMMARY);

    private FeedbackTaxonomy() {}

    public static CorrectionArea canonical(CorrectionArea area) {
        return area == CorrectionArea.GRAMMAR_MECHANICS ? CorrectionArea.GRAMMAR_SPELLING : area;
    }

    public static CorrectionArea areaOf(AiIssueKind kind) {
        return switch (kind) {
            case GRAMMAR, SPELLING, PUNCTUATION -> CorrectionArea.GRAMMAR_SPELLING;
            case VOCABULARY -> CorrectionArea.VOCABULARY_EXPRESSION;
            case SENTENCE_STRUCTURE -> CorrectionArea.SENTENCE_STRUCTURE;
            case CONTENT -> CorrectionArea.CONTENT_SUMMARY;
            default -> null;
        };
    }

    public static boolean validKinds(List<AiIssueKind> kinds) {
        return kinds != null
                && !kinds.isEmpty()
                && kinds.stream().noneMatch(java.util.Objects::isNull)
                && Set.copyOf(kinds).size() == kinds.size()
                && ISSUE_KINDS.containsAll(kinds);
    }
}
