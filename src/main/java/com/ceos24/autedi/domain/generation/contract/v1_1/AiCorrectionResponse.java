package com.ceos24.autedi.domain.generation.contract.v1_1;

import java.util.List;

/** v1.1 AI 응답의 최상위 객체. 빈 items는 첨삭 후보가 없는 정상 형식이다. */
public record AiCorrectionResponse(List<AiCorrectionItem> items) {
    public AiCorrectionResponse {
        items = List.copyOf(items);
    }
}
