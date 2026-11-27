package com.ceos24.autedi.domain.worksheet.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@Schema(description = "홈 최근 저장 작업 페이지 조건")
public record RecentWorksheetSearchRequest(
        @Min(value = 0, message = "페이지는 0 이상이어야 합니다.") @Schema(defaultValue = "0") Integer page,
        @Min(value = 1, message = "페이지 크기는 1 이상이어야 합니다.")
                @Max(value = 100, message = "페이지 크기는 100 이하여야 합니다.")
                @Schema(defaultValue = "20")
                Integer size) {
    public RecentWorksheetSearchRequest {
        page = page == null ? 0 : page;
        size = size == null ? 20 : size;
    }
}
