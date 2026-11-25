package com.ceos24.autedi.domain.academy.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "기관명 수정 요청")
public record AcademyUpdateRequest(
        @Schema(description = "새 기관명 (최대 100자)", example = "에이플러스 학원")
                @NotBlank(message = "기관명은 필수 입력값입니다.")
                @Size(max = 100, message = "기관명은 100자 이하여야 합니다.")
                String name) {}
