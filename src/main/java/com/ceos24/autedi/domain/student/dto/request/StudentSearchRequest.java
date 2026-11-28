package com.ceos24.autedi.domain.student.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

@Schema(description = "학생 목록 검색 조건")
public record StudentSearchRequest(
        @Schema(description = "이름 일부 또는 전체. 비어 있으면 전체", example = "김") @Size(max = 50, message = "검색어는 50자 이하여야 합니다.")
                String keyword,
        @Schema(description = "학년 일치 필터. 비어 있으면 전체", example = "초등 3") @Size(max = 50, message = "학년은 50자 이하여야 합니다.")
                String grade,
        @Schema(description = "반 이름 일치 필터. 비어 있으면 전체", example = "오후반")
                @Size(max = 100, message = "수강반은 100자 이하여야 합니다.")
                String className,
        @Schema(description = "학년 ID 필터. grade와 동시 사용 불가") @Positive Long gradeId,
        @Schema(description = "반 ID 필터. className·unassigned와 동시 사용 불가") @Positive Long classId,
        @Schema(description = "반 미배정만 조회. className·classId와 동시 사용 불가", defaultValue = "false") Boolean unassigned,
        @Schema(description = "0부터 시작하는 페이지", defaultValue = "0") @Min(value = 0, message = "페이지는 0 이상이어야 합니다.")
                Integer page,
        @Schema(description = "페이지당 학생 수 (1~100)", defaultValue = "20")
                @Min(value = 1, message = "페이지 크기는 1 이상이어야 합니다.")
                @Max(value = 100, message = "페이지 크기는 100 이하여야 합니다.")
                Integer size) {

    public StudentSearchRequest {
        keyword = optionalText(keyword);
        grade = optionalText(grade);
        className = optionalText(className);
        unassigned = Boolean.TRUE.equals(unassigned);
        page = page == null ? 0 : page;
        size = size == null ? 20 : size;
    }

    private static String optionalText(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
