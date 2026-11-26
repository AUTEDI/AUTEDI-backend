package com.ceos24.autedi.domain.worksheet.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

@Schema(description = "학습지 제목·학생 지정 요청. 두 값을 함께 전달합니다.")
public record WorksheetMetadataUpdateRequest(
        @Schema(description = "학습지 제목. 앞뒤 공백 제거 후 최대 255자", example = "11월 영어 작문")
                @NotBlank(message = "학습지 제목은 필수입니다.")
                @Size(max = 255, message = "학습지 제목은 255자 이하여야 합니다.")
                String title,
        @Schema(description = "같은 기관에 등록된 학생 ID", example = "17")
                @NotNull(message = "학생 지정은 필수입니다.")
                @Positive(message = "학생 ID는 양수여야 합니다.")
                Long studentId) {

    public WorksheetMetadataUpdateRequest {
        title = title == null ? null : title.strip();
    }
}
