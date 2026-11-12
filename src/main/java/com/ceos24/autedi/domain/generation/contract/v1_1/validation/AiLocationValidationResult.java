package com.ceos24.autedi.domain.generation.contract.v1_1.validation;

import com.ceos24.autedi.domain.generation.contract.v1_1.AiAction;
import com.ceos24.autedi.domain.generation.contract.v1_1.AiCorrectionItem;
import java.util.List;
import java.util.Objects;

/** 원본 후보와 위치 검사 결과. 정상 카드 판정·개수 제한·레이아웃 배치 결과가 아니다. */
public record AiLocationValidationResult(
        int pageNumber,
        int candidateIndex,
        AiCorrectionItem original,
        String originalText,
        List<AiResolvedAnchor> resolvedAnchors,
        List<List<Double>> validTargetBoxes,
        List<AiValidationIssue> issues) {
    public AiLocationValidationResult {
        Objects.requireNonNull(original, "original");
        resolvedAnchors = List.copyOf(resolvedAnchors);
        validTargetBoxes = validTargetBoxes.stream().map(List::copyOf).toList();
        issues = List.copyOf(issues);
        if ((originalText == null) != resolvedAnchors.isEmpty()) {
            throw new IllegalArgumentException("확인된 원문과 전체 연결 범위가 함께 존재해야 합니다.");
        }
        if (originalText == null && !validTargetBoxes.isEmpty()) {
            throw new IllegalArgumentException("원문 연결 없이 표시 좌표를 제공할 수 없습니다.");
        }
    }

    public boolean sourceResolved() {
        return originalText != null;
    }

    public boolean requiresReview() {
        return original.action() == AiAction.REVIEW || !issues.isEmpty();
    }

    /** 위치 검사 범위에서만 판단한다. 실제 표시 전 다른 검증 정책과 레이아웃 검사를 거쳐야 한다. */
    public boolean canAutoPlace() {
        return original.action() == AiAction.REPLACE
                && sourceResolved()
                && !original.locationUncertain()
                && !validTargetBoxes.isEmpty();
    }
}
