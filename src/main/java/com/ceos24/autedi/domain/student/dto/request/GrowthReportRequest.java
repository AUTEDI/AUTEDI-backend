package com.ceos24.autedi.domain.student.dto.request;

import com.ceos24.autedi.domain.correction.entity.FeedbackTaxonomy;
import com.ceos24.autedi.domain.worksheet.entity.CorrectionArea;
import com.ceos24.autedi.global.exception.CustomException;
import com.ceos24.autedi.global.exception.ErrorCode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;

public record GrowthReportRequest(
        @Schema(description = "1M / 3M / 6M / ALL. 생략하면 3M", example = "3M")
                @Pattern(regexp = "1M|3M|6M|ALL", message = "기간은 1M, 3M, 6M, ALL 중 하나여야 합니다.")
                String period,
        @Schema(description = "생략하면 4개 영역 모두 반환") CorrectionArea area) {
    public GrowthReportRequest {
        period = period == null ? "3M" : period;
        area = FeedbackTaxonomy.canonical(area);
    }

    public LocalDate from(LocalDate today) {
        return switch (period) {
            case "1M" -> today.minusMonths(1);
            case "3M" -> today.minusMonths(3);
            case "6M" -> today.minusMonths(6);
            case "ALL" -> null;
            default -> throw new CustomException(ErrorCode.INVALID_INPUT_VALUE, "기간은 1M, 3M, 6M, ALL 중 하나여야 합니다.");
        };
    }
}
