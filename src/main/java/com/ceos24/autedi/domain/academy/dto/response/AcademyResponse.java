package com.ceos24.autedi.domain.academy.dto.response;

import com.ceos24.autedi.domain.academy.entity.Academy;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "기관 정보 응답")
public record AcademyResponse(
        @Schema(description = "기관 ID", example = "1") Long academyId,
        @Schema(description = "기관명", example = "에이플러스 학원") String name) {

    public static AcademyResponse from(Academy academy) {
        return new AcademyResponse(academy.getId(), academy.getName());
    }
}
