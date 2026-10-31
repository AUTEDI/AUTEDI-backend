package com.ceos24.autedi.domain.correction.dto.response;

import com.ceos24.autedi.domain.correction.entity.CorrectionAnchor;

public record CorrectionAnchorResponse(Long transcriptionId, int lineId, int occurrence, int start, int end) {
    public static CorrectionAnchorResponse from(CorrectionAnchor anchor) {
        return anchor == null
                ? null
                : new CorrectionAnchorResponse(
                        anchor.transcriptionId(), anchor.lineId(), anchor.occurrence(), anchor.start(), anchor.end());
    }
}
