package com.ceos24.autedi.domain.comment.dto.response;

import java.util.List;

public record CommentListResponse(List<CommentResponse> items, int total) {
    public static CommentListResponse of(List<CommentResponse> items) {
        return new CommentListResponse(items, items != null ? items.size() : 0);
    }
}
