package com.ceos24.autedi.domain.generation.contract.v1;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** 모델이 반환한 후보이며 서버 검증 완료 결과가 아니다. boxes는 [left, top, right, bottom] 순서다. */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record AiCorrectionItem(
        AiAction action,
        @JsonProperty("feedback_type") AiFeedbackType feedbackType,
        AiArea area,
        @JsonProperty("issue_kinds") List<AiIssueKind> issueKinds,
        AiAnchor anchor,
        String replacement,
        String guidance,
        String reason,
        @JsonProperty("location_uncertain") boolean locationUncertain,
        List<List<Double>> boxes) {
    public AiCorrectionItem {
        issueKinds = List.copyOf(issueKinds);
        boxes = boxes.stream().map(List::copyOf).toList();
    }
}
