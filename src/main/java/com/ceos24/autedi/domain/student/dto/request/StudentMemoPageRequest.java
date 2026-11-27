package com.ceos24.autedi.domain.student.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@Schema(description = "학생 메모 목록 페이지 조건")
public record StudentMemoPageRequest(
        @Schema(description = "0부터 시작하는 페이지", defaultValue = "0") @Min(value = 0, message = "페이지는 0 이상이어야 합니다.")
                Integer page,
        @Schema(description = "페이지당 메모 수 (1~100)", defaultValue = "20")
                @Min(value = 1, message = "페이지 크기는 1 이상이어야 합니다.")
                @Max(value = 100, message = "페이지 크기는 100 이하여야 합니다.")
                Integer size) {

    public StudentMemoPageRequest {
        page = page == null ? 0 : page;
        size = size == null ? 20 : size;
    }
}
