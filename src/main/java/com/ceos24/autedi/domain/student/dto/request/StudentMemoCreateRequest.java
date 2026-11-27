package com.ceos24.autedi.domain.student.dto.request;

import com.ceos24.autedi.domain.student.entity.StudentMemo;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "학생 메모 등록 요청")
public record StudentMemoCreateRequest(
        @Schema(description = "메모 내용. 앞뒤 공백 제거 후 1~1,000자", example = "다음 주 시험 범위를 안내함")
                @NotBlank(message = "메모 내용은 필수입니다.")
                @Size(max = StudentMemo.MAX_CONTENT_LENGTH, message = "메모는 1,000자 이하여야 합니다.")
                String content) {

    public StudentMemoCreateRequest {
        content = content == null ? null : content.strip();
    }
}
