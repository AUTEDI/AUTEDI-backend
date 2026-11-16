package com.ceos24.autedi.domain.generation.contract.v1_1.validation;

import com.ceos24.autedi.domain.generation.contract.v1_1.AiAction;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/** 원문·좌표 결과에 수정 동작 진단을 추가한다. 원본 후보와 앞 단계 진단을 보존한다. */
public record AiEditValidationResult(AiLocationValidationResult location, List<AiValidationIssue> editIssues) {
    public AiEditValidationResult {
        Objects.requireNonNull(location, "location");
        editIssues = List.copyOf(editIssues);
    }

    public AiEditValidationStatus status() {
        if (!editIssues.isEmpty()) return AiEditValidationStatus.INCONSISTENT;
        if (location.original().action() != AiAction.REVIEW && !location.sourceResolved()) {
            return AiEditValidationStatus.SOURCE_UNRESOLVED;
        }
        return AiEditValidationStatus.CONSISTENT;
    }

    public List<AiValidationIssue> issues() {
        return Stream.concat(location.issues().stream(), editIssues.stream()).toList();
    }

    public boolean requiresReview() {
        return location.requiresReview() || status() != AiEditValidationStatus.CONSISTENT;
    }

    /** 위치·동작 단계의 조건만 만족한다는 뜻이다. 이후 정책 검증과 실제 레이아웃을 거쳐야 한다. */
    public boolean canAutoPlace() {
        return status() == AiEditValidationStatus.CONSISTENT && location.canAutoPlace();
    }
}
