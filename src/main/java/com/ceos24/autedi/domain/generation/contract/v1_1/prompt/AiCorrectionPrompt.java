package com.ceos24.autedi.domain.generation.contract.v1_1.prompt;

import com.ceos24.autedi.domain.generation.contract.v1_1.AiArea;
import com.ceos24.autedi.domain.generation.contract.v1_1.validation.AiCorrectionLevel;
import java.util.Map;
import java.util.Objects;

/** 첨삭 단계의 불변 프롬프트 묶음. 이미지·인증정보·전송 가능한 HTTP 요청은 포함하지 않는다. */
public record AiCorrectionPrompt(
        String model,
        String contractId,
        String promptVersion,
        AiCorrectionPromptVariant variant,
        Map<AiArea, AiCorrectionLevel> selectedAreas,
        String instructions,
        String inputText,
        String responseSchema) {
    public AiCorrectionPrompt {
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(contractId, "contractId");
        Objects.requireNonNull(variant, "variant");
        Objects.requireNonNull(instructions, "instructions");
        Objects.requireNonNull(inputText, "inputText");
        Objects.requireNonNull(responseSchema, "responseSchema");
        selectedAreas = Map.copyOf(selectedAreas);
        if (!variant.promptVersion().equals(promptVersion)) {
            throw new IllegalArgumentException("프롬프트 후보와 버전명이 일치해야 합니다.");
        }
    }
}
