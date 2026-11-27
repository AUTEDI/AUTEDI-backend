package com.ceos24.autedi.domain.student.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;

@Schema(description = "학생 저장 결과물 검색 조건. 기간은 최근 명시적 저장일 기준이며 양 끝 날짜를 포함합니다.")
public record StudentWorksheetSearchRequest(
        @Size(max = 255, message = "검색어는 255자 이하여야 합니다.") @Schema(description = "제목 부분 검색. 공백만 입력하면 전체") String keyword,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @Schema(description = "시작 저장일 (YYYY-MM-DD)") LocalDate from,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @Schema(description = "종료 저장일 (YYYY-MM-DD), 해당 날짜 전체 포함")
                LocalDate to,
        @Min(value = 0, message = "페이지는 0 이상이어야 합니다.") @Schema(defaultValue = "0") Integer page,
        @Min(value = 1, message = "페이지 크기는 1 이상이어야 합니다.")
                @Max(value = 100, message = "페이지 크기는 100 이하여야 합니다.")
                @Schema(defaultValue = "20")
                Integer size) {
    public StudentWorksheetSearchRequest {
        keyword = keyword == null || keyword.isBlank() ? null : keyword.strip();
        page = page == null ? 0 : page;
        size = size == null ? 20 : size;
    }
}
