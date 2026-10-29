package com.ceos24.autedi.domain.worksheet.dto.response;

import com.ceos24.autedi.domain.worksheet.entity.CorrectionArea;
import com.ceos24.autedi.domain.worksheet.entity.CorrectionLevel;

public record SettingItemResponse(
        CorrectionArea area,
        CorrectionLevel level
) {
}
