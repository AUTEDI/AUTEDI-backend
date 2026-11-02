package com.ceos24.autedi.domain.generation.contract.v1.prompt;

import com.ceos24.autedi.domain.generation.contract.v1.AiArea;
import com.ceos24.autedi.domain.generation.contract.v1.validation.AiCorrectionLevel;
import java.util.Map;

/** 전사 후 두 번째 첨삭 단계의 프롬프트 묶음. 이미지 첨부나 HTTP 요청 본문은 포함하지 않는다. */
public record AiCorrectionPrompt(
        String model,
        String contractId,
        String promptVersion,
        Map<AiArea, AiCorrectionLevel> selectedAreas,
        String instructions,
        String inputText,
        String responseSchema) {
    public AiCorrectionPrompt {
        selectedAreas = Map.copyOf(selectedAreas);
    }
}
