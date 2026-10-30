package com.ceos24.autedi.domain.generation.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "AI 생성 실패 오류 상세 정보")
public record GenerationErrorResponse(
        @Schema(description = "오류 코드", example = "AI_TIMEOUT") String code,
        @Schema(description = "오류 상세 메시지", example = "AI 모델 응답 시간이 초과되었습니다.") String message,
        @Schema(description = "재시도 가능 여부", example = "true") Boolean retryable) {}
