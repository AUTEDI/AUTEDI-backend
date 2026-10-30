package com.ceos24.autedi.domain.comment.dto.response;

import com.ceos24.autedi.domain.comment.entity.Comment;
import com.ceos24.autedi.domain.comment.entity.HighlightType;
import com.ceos24.autedi.domain.correction.entity.RegionPayload;
import java.time.LocalDateTime;
import java.util.List;

public record CommentResponse(
        Long id,
        String etag,
        String source,
        Long pageId,
        HighlightType highlightType,
        List<RegionPayload> regions,
        String content,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
    public static CommentResponse from(Comment comment) {
        return of(comment, comment.getEtag());
    }

    public static CommentResponse of(Comment comment, String etag) {
        return new CommentResponse(
                comment.getId(),
                etag,
                "TEACHER",
                comment.getPage() != null ? comment.getPage().getId() : null,
                comment.getHighlightType(),
                comment.getRegions(),
                comment.getContent(),
                comment.getCreatedAt(),
                comment.getUpdatedAt());
    }
}
