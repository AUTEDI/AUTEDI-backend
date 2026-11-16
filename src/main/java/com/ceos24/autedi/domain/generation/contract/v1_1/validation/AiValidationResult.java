package com.ceos24.autedi.domain.generation.contract.v1_1.validation;

import com.ceos24.autedi.domain.generation.contract.v1_1.AiAction;
import com.ceos24.autedi.domain.generation.contract.v1_1.AiCorrectionResponse;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** 전체 후보는 AI 순서로 보존하고 일반 카드·확인 목록은 원문 순서로 제공한다. */
public record AiValidationResult(
        AiCorrectionResponse originalResponse,
        AiValidationContext context,
        List<AiValidatedItem> candidates,
        List<AiAreaSummary> summary) {
    private static final Comparator<AiValidatedItem> TEXT_ORDER = Comparator.comparingInt(
                    (AiValidatedItem item) -> firstAnchor(item) == null
                            ? Integer.MAX_VALUE
                            : firstAnchor(item).lineId())
            .thenComparingInt(item -> firstAnchor(item) == null
                    ? Integer.MAX_VALUE
                    : firstAnchor(item).start())
            .thenComparingInt(AiValidatedItem::candidateIndex);

    public AiValidationResult {
        Objects.requireNonNull(originalResponse, "originalResponse");
        Objects.requireNonNull(context, "context");
        candidates = List.copyOf(candidates);
        summary = List.copyOf(summary);
    }

    public List<AiValidatedItem> cards() {
        return displayItems(AiItemDisposition.CARD);
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
        return (int) candidates.stream()
                .filter(item ->
                        item.original().action() != AiAction.REVIEW && item.disposition() != AiItemDisposition.CARD)
                .count();
    }

    private List<AiValidatedItem> displayItems(AiItemDisposition disposition) {
        return candidates.stream()
                .filter(item -> item.disposition() == disposition)
                .sorted(TEXT_ORDER)
                .toList();
    }

    private static AiResolvedAnchor firstAnchor(AiValidatedItem item) {
        return item.location().resolvedAnchors().isEmpty()
                ? null
                : item.location().resolvedAnchors().getFirst();
    }
}
