package com.ceos24.autedi.domain.comment.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "선생님 코멘트 내용 수정 요청")
public record CommentUpdateRequest(
        @Schema(description = "수정할 코멘트 내용 (영역 좌표는 불변 유지)", example = "수정된 코멘트: 시제 일치에도 주의해주세요.")
                @NotBlank(message = "코멘트 내용을 입력해 주세요.")
                String content) {}
