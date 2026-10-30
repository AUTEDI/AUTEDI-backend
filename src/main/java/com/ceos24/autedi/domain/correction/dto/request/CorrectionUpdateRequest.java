package com.ceos24.autedi.domain.correction.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "AI 첨삭 내용(content) 수정 요청")
public record CorrectionUpdateRequest(
        @Schema(description = "선생님이 직접 수정한 수정문 또는 제안문 내용", example = "He goes to school every day.")
                @NotBlank(message = "수정할 내용을 입력해 주세요.")
                String content) {}
