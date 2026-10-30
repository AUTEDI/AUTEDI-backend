package com.ceos24.autedi.domain.comment.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "선생님 코멘트 목록 응답")
public record CommentListResponse(
        @Schema(description = "코멘트 목록") List<CommentResponse> items,
        @Schema(description = "조회된 전체 코멘트 수", example = "3") int total) {
    public static CommentListResponse of(List<CommentResponse> items) {
        return new CommentListResponse(items, items != null ? items.size() : 0);
    }
}
