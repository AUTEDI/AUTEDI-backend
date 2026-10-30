package com.ceos24.autedi.domain.correction.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "AI 첨삭 항목 목록 응답")
public record CorrectionListResponse(
        @Schema(description = "첨삭 항목 목록") List<CorrectionResponse> items,
        @Schema(description = "조회된 전체 항목 수", example = "5") int total) {
    public static CorrectionListResponse of(List<CorrectionResponse> items) {
        return new CorrectionListResponse(items, items != null ? items.size() : 0);
    }
}
