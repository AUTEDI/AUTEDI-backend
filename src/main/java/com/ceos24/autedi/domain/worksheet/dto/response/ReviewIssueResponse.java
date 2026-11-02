package com.ceos24.autedi.domain.worksheet.dto.response;

import com.ceos24.autedi.domain.generation.contract.v1.AiCorrectionItem;
import com.ceos24.autedi.domain.generation.contract.v1.validation.AiResolvedAnchor;
import com.ceos24.autedi.domain.generation.contract.v1.validation.AiValidationIssue;
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
        List<AiValidationIssue> warnings,
        boolean requiresReview) {}
