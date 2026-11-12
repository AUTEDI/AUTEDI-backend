package com.ceos24.autedi.domain.generation.contract.v1_1.validation;

import com.ceos24.autedi.domain.generation.contract.v1_1.AiArea;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 같은 페이지의 확정 전사와 생성 당시 영역별 레벨 스냅샷. 현재 프론트 요청 DTO와는 별개다. */
public record AiValidationContext(AiLocationContext location, Map<AiArea, AiCorrectionLevel> selectedAreas) {
    public AiValidationContext {
        Objects.requireNonNull(location, "location");
        selectedAreas = Map.copyOf(selectedAreas);
    }

    public AiValidationContext(
            int pageNumber, List<String> transcriptionLines, Map<AiArea, AiCorrectionLevel> selectedAreas) {
        this(new AiLocationContext(pageNumber, transcriptionLines), selectedAreas);
    }

    public static AiValidationContext fromText(
            int pageNumber, String transcription, Map<AiArea, AiCorrectionLevel> selectedAreas) {
        return new AiValidationContext(AiLocationContext.fromText(pageNumber, transcription), selectedAreas);
    }
}
