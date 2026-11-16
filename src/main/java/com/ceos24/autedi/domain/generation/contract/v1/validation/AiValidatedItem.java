package com.ceos24.autedi.domain.generation.contract.v1.validation;

import com.ceos24.autedi.domain.generation.contract.v1.AiCorrectionItem;
import java.util.List;

/** candidateIndex는 원본 items의 0 기반 인덱스. original은 수정하거나 잘라내지 않는다. */
public record AiValidatedItem(
        int pageNumber,
        int candidateIndex,
        AiCorrectionItem original,
        AiItemDisposition disposition,
        AiResolvedAnchor resolvedAnchor,
        List<List<Double>> validBoxes,
        List<AiValidationIssue> issues) {
    public AiValidatedItem {
        validBoxes = validBoxes.stream().map(List::copyOf).toList();
        issues = List.copyOf(issues);
    }

    public boolean requiresReview() {
        return disposition == AiItemDisposition.REVIEW || !issues.isEmpty();
    }
}
