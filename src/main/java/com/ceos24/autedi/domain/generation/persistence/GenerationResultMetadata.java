package com.ceos24.autedi.domain.generation.persistence;

import com.ceos24.autedi.domain.generation.contract.v1.AiCorrectionResponseParser;
import com.ceos24.autedi.domain.generation.contract.v1.prompt.AiCorrectionPromptComposer;

/** 요청·검증 당시 버전이며, 복원 시 현재 코드의 버전으로 바꾸지 않는다. */
public record GenerationResultMetadata(
        String model, String contractVersion, String promptVersion, String validationVersion) {
    public static final String VALIDATION_VERSION = "AUTEDI-VALIDATION-v1";

    public GenerationResultMetadata {
        for (String value : new String[] {model, contractVersion, promptVersion, validationVersion}) {
            if (value == null || value.isBlank() || value.length() > 100) {
                throw new IllegalArgumentException("모델·계약·프롬프트·검증 버전은 1~100자여야 합니다.");
            }
        }
    }

    public static GenerationResultMetadata current() {
        return new GenerationResultMetadata(
                AiCorrectionPromptComposer.MODEL,
                AiCorrectionResponseParser.CONTRACT_ID,
                AiCorrectionPromptComposer.PROMPT_VERSION,
                VALIDATION_VERSION);
    }
}
