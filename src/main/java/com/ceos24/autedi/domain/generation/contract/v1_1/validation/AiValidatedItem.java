package com.ceos24.autedi.domain.generation.contract.v1_1.validation;

import com.ceos24.autedi.domain.generation.contract.v1_1.AiCorrectionItem;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/** 원본과 앞 단계 결과를 보존한 한 후보의 정책 판정. 주석·저장·프론트 응답 객체는 아니다. */
public record AiValidatedItem(
        AiEditValidationResult editValidation, AiItemDisposition disposition, List<AiValidationIssue> policyIssues) {
    public AiValidatedItem {
        Objects.requireNonNull(editValidation, "editValidation");
        Objects.requireNonNull(disposition, "disposition");
        policyIssues = List.copyOf(policyIssues);
    }

    public AiLocationValidationResult location() {
        return editValidation.location();
    }

    public AiCorrectionItem original() {
        return location().original();
    }

    public int candidateIndex() {
        return location().candidateIndex();
    }

    public List<AiValidationIssue> issues() {
        return Stream.concat(editValidation.issues().stream(), policyIssues.stream())
                .toList();
    }

    public boolean requiresReview() {
        return disposition == AiItemDisposition.REVIEW || editValidation.requiresReview() || !policyIssues.isEmpty();
    }

    /** 일반 카드의 위치·동작·정책 조건만 확인한다. 실제 이미지 배치는 렌더러가 수행한다. */
    public boolean canAutoPlace() {
        return disposition == AiItemDisposition.CARD && policyIssues.isEmpty() && editValidation.canAutoPlace();
    }
}
