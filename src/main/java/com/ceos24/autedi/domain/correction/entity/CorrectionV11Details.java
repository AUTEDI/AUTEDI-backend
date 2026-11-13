package com.ceos24.autedi.domain.correction.entity;

import com.ceos24.autedi.domain.generation.contract.v1_1.AiEdit;
import com.ceos24.autedi.domain.generation.contract.v1_1.AiRevisionScope;
import com.ceos24.autedi.domain.generation.contract.v1_1.validation.AiValidationIssue;
import java.util.List;

/** v1.1 카드의 전체 원문 연결·최초/현재 동작·원래 진단 코드. 기존 단일 anchor로 축약하지 않는다. */
public record CorrectionV11Details(
        List<CorrectionAnchor> anchors,
        AiRevisionScope revisionScope,
        AiEdit aiInitialEdit,
        AiEdit edit,
        List<AiValidationIssue> warnings) {
    public CorrectionV11Details {
        anchors = List.copyOf(anchors);
        warnings = List.copyOf(warnings);
        if (anchors.isEmpty()) throw new IllegalArgumentException("v1.1 카드는 전체 원문 연결이 필요합니다.");
        for (int i = 1; i < anchors.size(); i++) {
            if (!anchors.getFirst().transcriptionId().equals(anchors.get(i).transcriptionId())
                    || anchors.get(i).lineId() != anchors.get(i - 1).lineId() + 1) {
                throw new IllegalArgumentException("v1.1 원문은 같은 전사의 연속된 줄이어야 합니다.");
            }
        }
    }

    public CorrectionV11Details invalidateEdit() {
        return new CorrectionV11Details(anchors, revisionScope, aiInitialEdit, null, warnings);
    }
}
