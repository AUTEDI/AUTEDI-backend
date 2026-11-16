package com.ceos24.autedi.domain.comment.dto.request;

import com.ceos24.autedi.domain.comment.entity.CommentColor;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "선생님 코멘트 내용/색상 수정 요청")
public record CommentUpdateRequest(
        @Schema(description = "수정할 코멘트 내용", example = "단수 주어(He) 다음에는 동사원형 뒤에 -s/-es를 붙입니다.")
                @NotBlank(message = "수정할 코멘트 내용을 입력해 주세요.")
                String content,
        @Schema(description = "수정할 표시 색상 (선택 사항)", example = "RED") CommentColor color) {
    public CommentUpdateRequest(String content) {
        this(content, null);
    }
}
