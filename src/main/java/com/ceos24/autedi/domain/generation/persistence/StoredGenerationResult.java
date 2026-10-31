package com.ceos24.autedi.domain.generation.persistence;

import com.ceos24.autedi.domain.generation.contract.v1.validation.AiValidationResult;
import com.ceos24.autedi.domain.generation.entity.GenerationResultStatus;
import java.util.List;

/** 최초 모델 응답·검증 결과와 현재 편집 가능한 카드의 ID를 구분해 복원한다. */
public record StoredGenerationResult(
        Long id,
        Long generationId,
        StoredTranscription transcription,
        GenerationResultStatus status,
        GenerationResultMetadata metadata,
        String rawResponse,
        AiValidationResult validationResult,
        List<StoredCorrectionLink> correctionLinks,
        String parseError) {
    public StoredGenerationResult {
        correctionLinks = List.copyOf(correctionLinks);
    }
}
