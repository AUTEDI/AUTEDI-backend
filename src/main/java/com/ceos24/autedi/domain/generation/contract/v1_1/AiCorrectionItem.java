package com.ceos24.autedi.domain.generation.contract.v1_1;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** 모델의 미검증 후보. 동작·레벨·원문·좌표 간 관계는 후속 파서와 검증기에서 검사한다. */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record AiCorrectionItem(
        AiAction action,
        @JsonProperty("feedback_type") AiFeedbackType feedbackType,
        AiArea area,
        @JsonProperty("issue_kinds") List<AiIssueKind> issueKinds,
        List<AiAnchor> anchors,
        String replacement,
        String guidance,
        String reason,
        @JsonProperty("location_uncertain") boolean locationUncertain,
        @JsonProperty("target_boxes") List<List<Double>> targetBoxes,
        @JsonProperty("revision_scope") AiRevisionScope revisionScope,
        AiEdit edit) {
    public AiCorrectionItem {
        issueKinds = List.copyOf(issueKinds);
        anchors = List.copyOf(anchors);
        targetBoxes = targetBoxes.stream().map(List::copyOf).toList();
    }
}
