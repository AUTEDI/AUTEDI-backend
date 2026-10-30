package com.ceos24.autedi.domain.comment.dto.response;

import com.ceos24.autedi.domain.comment.entity.Comment;
import com.ceos24.autedi.domain.comment.entity.HighlightType;
import com.ceos24.autedi.domain.correction.entity.RegionPayload;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "선생님 코멘트 상세 정보")
public record CommentResponse(
        @Schema(description = "코멘트 고유 ID", example = "301") Long id,
        @Schema(description = "동시성 제어용 ETag", example = "\"com_301-v1\"") String etag,
        @Schema(description = "작성 주체 (항상 TEACHER)", example = "TEACHER") String source,
        @Schema(description = "대상 페이지 ID", example = "10") Long pageId,
        @Schema(description = "강조 유형 (HIGHLIGHT: 형광펜, UNDERLINE: 밑줄)", example = "HIGHLIGHT")
                HighlightType highlightType,
        @Schema(description = "강조된 캔버스 영역 좌표 목록") List<RegionPayload> regions,
        @Schema(description = "코멘트 텍스트 내용", example = "단수 주어(He) 다음에는 3인칭 단수 동사 goes를 사용해야 합니다.") String content,
        @Schema(description = "작성 일시", example = "2026-10-30T10:10:00") LocalDateTime createdAt,
        @Schema(description = "수정 일시", example = "2026-10-30T10:12:00") LocalDateTime updatedAt) {
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
