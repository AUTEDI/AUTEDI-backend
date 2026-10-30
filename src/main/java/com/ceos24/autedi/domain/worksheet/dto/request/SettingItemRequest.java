package com.ceos24.autedi.domain.worksheet.dto.request;

import com.ceos24.autedi.domain.worksheet.entity.CorrectionArea;
import com.ceos24.autedi.domain.worksheet.entity.CorrectionLevel;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "개별 첨삭 영역 및 수준 설정 항목")
public record SettingItemRequest(
        @Schema(
                        description =
                                "첨삭 영역 (GRAMMAR_SPELLING: 문법/철자, VOCABULARY_EXPRESSION: 어휘/표현, SENTENCE_STRUCTURE: 문장구조, CONTENT_SUMMARY: 내용요약)",
                        example = "GRAMMAR_SPELLING")
                @NotNull(message = "첨삭 영역을 지정해야 합니다.")
                CorrectionArea area,
        @Schema(description = "AI 개입 수준 (STANDARD: 표준 개입, ADVANCED: 심화 개입)", example = "STANDARD")
                @NotNull(message = "AI 개입 수준을 지정해야 합니다.")
                CorrectionLevel level) {}
