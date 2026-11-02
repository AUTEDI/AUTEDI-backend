package com.ceos24.autedi.domain.generation.contract.v1.validation;

import com.ceos24.autedi.domain.generation.contract.v1.AiCorrectionResponse;
import java.util.Comparator;
import java.util.List;

/** candidates는 모델 순서, 화면 목록은 원문 순서다. 원본 응답과 전사·설정 스냅샷을 함께 보존한다. */
public record AiValidationResult(
        AiCorrectionResponse originalResponse,
        AiValidationContext context,
        List<AiValidatedItem> candidates,
        List<AiAreaSummary> summary) {
    private static final Comparator<AiValidatedItem> TEXT_ORDER = Comparator.comparingInt(
                    (AiValidatedItem item) -> item.resolvedAnchor() == null
                            ? Integer.MAX_VALUE
                            : item.resolvedAnchor().lineId())
            .thenComparingInt(item -> item.resolvedAnchor() == null
                    ? Integer.MAX_VALUE
                    : item.resolvedAnchor().start())
            .thenComparingInt(AiValidatedItem::candidateIndex);

    public AiValidationResult {
        candidates = List.copyOf(candidates);
        summary = List.copyOf(summary);
    }

    public List<AiValidatedItem> corrections() {
        return displayItems(AiItemDisposition.CORRECTION);
    }

    public List<AiValidatedItem> reviewIssues() {
        return displayItems(AiItemDisposition.REVIEW);
    }

    public List<AiValidatedItem> excludedItems() {
        return candidates.stream()
                .filter(item -> item.disposition() == AiItemDisposition.EXCLUDED)
                .toList();
    }

    public int omittedCount() {
        return excludedItems().size();
    }

    private List<AiValidatedItem> displayItems(AiItemDisposition disposition) {
        return candidates.stream()
                .filter(item -> item.disposition() == disposition)
                .sorted(TEXT_ORDER)
                .toList();
    }
}
