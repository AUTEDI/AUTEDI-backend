package com.ceos24.autedi.domain.correction.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "AI 첨삭 내용(content) 수정 요청")
public record CorrectionUpdateRequest(
        @Schema(description = "선생님이 직접 수정한 수정문 또는 제안문 내용", example = "He goes to school every day.")
                @NotNull(message = "수정 내용은 null일 수 없습니다. REPLACE의 빈 문자열은 삭제 제안입니다.")
                String content) {}
