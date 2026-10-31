package com.ceos24.autedi.domain.generation.contract.v1;

import java.util.List;

/** AUTEDI-AI-CONTRACT-v1 응답. 빈 items는 첨삭 후보가 없는 정상 응답이다. */
public record AiCorrectionResponse(List<AiCorrectionItem> items) {
    public AiCorrectionResponse {
        items = List.copyOf(items);
    }
}
