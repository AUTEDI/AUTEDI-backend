package com.ceos24.autedi.domain.worksheet.dto.response;

import com.ceos24.autedi.domain.worksheet.entity.CorrectionArea;
import com.ceos24.autedi.domain.worksheet.entity.CorrectionLevel;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "저장된 첨삭 설정 항목")
public record SettingItemResponse(
        @Schema(description = "첨삭 영역", example = "GRAMMAR_SPELLING") CorrectionArea area,
        @Schema(description = "AI 개입 수준 (STANDARD: 표준, ADVANCED: 심화)", example = "STANDARD") CorrectionLevel level) {}
