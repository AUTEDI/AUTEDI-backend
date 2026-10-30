package com.ceos24.autedi.domain.correction.dto.response;

import java.util.List;

public record CorrectionListResponse(List<CorrectionResponse> items, int total) {
    public static CorrectionListResponse of(List<CorrectionResponse> items) {
        return new CorrectionListResponse(items, items != null ? items.size() : 0);
    }
}
