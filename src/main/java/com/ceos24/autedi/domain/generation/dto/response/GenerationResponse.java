package com.ceos24.autedi.domain.generation.dto.response;

import com.ceos24.autedi.domain.generation.entity.GenerationStage;
import com.ceos24.autedi.domain.generation.entity.GenerationStatus;
import com.ceos24.autedi.domain.generation.entity.WorksheetGeneration;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "AI 첨삭 생성 작업 상태 응답")
public record GenerationResponse(
        @Schema(description = "생성 작업 고유 ID", example = "101") Long id,
        @Schema(description = "대상 학습지 ID", example = "1") Long worksheetId,
        @Schema(description = "작업 상태 (PENDING, RUNNING, SUCCEEDED, FAILED)", example = "SUCCEEDED")
                GenerationStatus status,
        @Schema(description = "현재 처리 단계 (QUEUED, OCR, AI_CORRECTION, COMPLETED)", example = "COMPLETED")
                GenerationStage stage,
        @Schema(description = "시도 횟수 (초기: 1)", example = "1") Integer attempt,
        @Schema(description = "재시도 대상 이전 작업 ID (신규 요청 시 null)", example = "null") Long retryOfGenerationId,
        @Schema(description = "요청 당시 첨삭 설정 스냅샷 (JSON)") Object settingsSnapshot,
        @Schema(description = "작업 접수 일시", example = "2026-10-30T10:00:00") LocalDateTime createdAt,
        @Schema(description = "작업 시작 일시", example = "2026-10-30T10:00:01") LocalDateTime startedAt,
        @Schema(description = "작업 완료/종료 일시", example = "2026-10-30T10:00:05") LocalDateTime finishedAt,
        @Schema(description = "실패 시 오류 상세 정보 (성공 시 null)") GenerationErrorResponse error) {
    public static GenerationResponse from(WorksheetGeneration gen) {
        GenerationErrorResponse error = null;
        if (gen.getErrorCode() != null) {
            error = new GenerationErrorResponse(gen.getErrorCode(), gen.getErrorMessage(), gen.getIsRetryable());
        }

        return new GenerationResponse(
                gen.getId(),
                gen.getWorksheet().getId(),
                gen.getStatus(),
                gen.getCurrentStage(),
                gen.getAttempt(),
                gen.getRetryOfGenerationId(),
                gen.getRequestSettingsSnapshot(),
                gen.getCreatedAt(),
                gen.getStartedAt(),
                gen.getCompletedAt(),
                error);
    }
}
