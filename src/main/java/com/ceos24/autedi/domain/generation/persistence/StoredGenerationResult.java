package com.ceos24.autedi.domain.generation.persistence;

import com.ceos24.autedi.domain.generation.contract.GenerationContractRouter;
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
        com.ceos24.autedi.domain.generation.contract.v1_1.validation.AiValidationResult validationResultV11,
        List<StoredCorrectionLink> correctionLinks,
        String parseError) {
    public StoredGenerationResult {
        correctionLinks = List.copyOf(correctionLinks);
        if (validationResult != null && validationResultV11 != null) {
            throw new IllegalArgumentException("두 계약의 검증 결과를 동시에 복원할 수 없습니다.");
        }
    }

    public GenerationContractRouter.Validation versionedValidationResult() {
        if (validationResult != null) return new GenerationContractRouter.V1Validation(validationResult);
        if (validationResultV11 != null) return new GenerationContractRouter.V11Validation(validationResultV11);
        return null;
    }

    /** 기존 v1 조회 코드가 v1.1을 검증 결과 없는 응답으로 오해하지 않게 한다. */
    @Override
    public AiValidationResult validationResult() {
        if (validationResultV11 != null) {
            throw new IllegalStateException("v1.1은 validationResultV11 또는 versionedValidationResult로 읽어야 합니다.");
        }
        return validationResult;
    }
}
