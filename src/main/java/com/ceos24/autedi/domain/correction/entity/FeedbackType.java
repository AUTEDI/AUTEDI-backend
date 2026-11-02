package com.ceos24.autedi.domain.correction.entity;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "AI 첨삭 피드백 유형 (CORRECTION: 오류 수정, SUGGESTION: 개선 제안)")
public enum FeedbackType {
    @Schema(description = "명백한 오류 수정 (문법, 철자 등)")
    CORRECTION,

    @Schema(description = "더 나은 표현 또는 어휘 개선 제안")
    SUGGESTION;

    public static FeedbackType fromString(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return FeedbackType.valueOf(value.trim().toUpperCase());
    }
}
