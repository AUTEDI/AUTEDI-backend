package com.ceos24.autedi.domain.generation.dto.response;

import com.ceos24.autedi.domain.generation.entity.GenerationStage;
import com.ceos24.autedi.domain.generation.entity.GenerationStatus;
import com.ceos24.autedi.domain.generation.entity.WorksheetGeneration;
import java.time.LocalDateTime;

public record GenerationResponse(
        Long id,
        Long worksheetId,
        GenerationStatus status,
        GenerationStage stage,
        Integer attempt,
        Long retryOfGenerationId,
        Object settingsSnapshot,
        LocalDateTime createdAt,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        GenerationErrorResponse error) {
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
