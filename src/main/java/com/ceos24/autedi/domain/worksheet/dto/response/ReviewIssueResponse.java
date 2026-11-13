package com.ceos24.autedi.domain.worksheet.dto.response;

import com.ceos24.autedi.domain.correction.dto.response.CorrectionWarningResponse;
import com.ceos24.autedi.domain.generation.contract.v1.AiAction;
import com.ceos24.autedi.domain.generation.contract.v1.AiAnchor;
import com.ceos24.autedi.domain.generation.contract.v1.AiArea;
import com.ceos24.autedi.domain.generation.contract.v1.AiCorrectionItem;
import com.ceos24.autedi.domain.generation.contract.v1.AiFeedbackType;
import com.ceos24.autedi.domain.generation.contract.v1.AiIssueKind;
import com.ceos24.autedi.domain.generation.contract.v1.validation.AiResolvedAnchor;
import com.ceos24.autedi.domain.generation.contract.v1.validation.AiValidatedItem;
import com.ceos24.autedi.domain.generation.persistence.StoredGenerationResult;
import java.util.List;

/** 정상 카드와 구분된 최초 확인 항목. original에 모델의 원래 제안도 보존한다. */
public record ReviewIssueResponse(
        Long resultId,
        Long generationId,
        Long transcriptionId,
        Long pageId,
        int pageNumber,
        int candidateIndex,
        AiCorrectionItem original,
        AiResolvedAnchor resolvedAnchor,
        List<List<Double>> validBoxes,
        List<CorrectionWarningResponse> warnings,
        boolean requiresReview,
        String contractVersion,
        com.ceos24.autedi.domain.generation.contract.v1_1.AiCorrectionItem originalV11,
        List<AiAnchor> anchors,
        List<AiResolvedAnchor> resolvedAnchors) {
    public static ReviewIssueResponse from(StoredGenerationResult saved, AiValidatedItem issue) {
        var transcript = saved.transcription();
        return new ReviewIssueResponse(
                saved.id(),
                saved.generationId(),
                transcript.id(),
                transcript.pageId(),
                issue.pageNumber(),
                issue.candidateIndex(),
                issue.original(),
                issue.resolvedAnchor(),
                issue.validBoxes(),
                issue.issues().stream().map(CorrectionWarningResponse::from).toList(),
                issue.requiresReview(),
                saved.metadata().contractVersion(),
                null,
                issue.original().anchor() == null
                        ? List.of()
                        : List.of(issue.original().anchor()),
                issue.resolvedAnchor() == null ? List.of() : List.of(issue.resolvedAnchor()));
    }

    public static ReviewIssueResponse from(
            StoredGenerationResult saved,
            com.ceos24.autedi.domain.generation.contract.v1_1.validation.AiValidatedItem issue) {
        var transcript = saved.transcription();
        var original = issue.original();
        List<AiAnchor> anchors = original.anchors().stream()
                .map(anchor -> new AiAnchor(anchor.lineId(), anchor.original(), anchor.occurrence()))
                .toList();
        List<AiResolvedAnchor> resolved = issue.location().resolvedAnchors().stream()
                .map(anchor -> new AiResolvedAnchor(anchor.lineId(), anchor.start(), anchor.end()))
                .toList();
        // 외부 응답 전용 호환 투영이다. 다중 줄을 단일 범위로 축소하지 않는다.
        var compatible = new AiCorrectionItem(
                AiAction.valueOf(original.action().name()),
                original.feedbackType() == null
                        ? null
                        : AiFeedbackType.valueOf(original.feedbackType().name()),
                original.area() == null ? null : AiArea.valueOf(original.area().name()),
                original.issueKinds().stream()
                        .map(kind -> AiIssueKind.valueOf(kind.name()))
                        .toList(),
                anchors.size() == 1 ? anchors.getFirst() : null,
                original.replacement(),
                original.guidance(),
                original.reason(),
                original.locationUncertain(),
                original.targetBoxes());
        return new ReviewIssueResponse(
                saved.id(),
                saved.generationId(),
                transcript.id(),
                transcript.pageId(),
                issue.location().pageNumber(),
                issue.candidateIndex(),
                compatible,
                anchors.size() == 1 && resolved.size() == 1 ? resolved.getFirst() : null,
                issue.location().validTargetBoxes(),
                issue.issues().stream().map(CorrectionWarningResponse::from).toList(),
                issue.requiresReview(),
                saved.metadata().contractVersion(),
                original,
                anchors,
                resolved);
    }
}
