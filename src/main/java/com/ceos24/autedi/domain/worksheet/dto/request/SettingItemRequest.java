package com.ceos24.autedi.domain.worksheet.dto.request;

import com.ceos24.autedi.domain.worksheet.entity.CorrectionArea;
import com.ceos24.autedi.domain.worksheet.entity.CorrectionLevel;
import jakarta.validation.constraints.NotNull;

public record SettingItemRequest(
        @NotNull(message = "첨삭 영역을 지정해야 합니다.")
        CorrectionArea area,

        @NotNull(message = "AI 개입 수준을 지정해야 합니다.")
        CorrectionLevel level
) {
}
