package com.ceos24.autedi.domain.comment.entity;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "선생님 코멘트 및 마커 표시 색상 (RED, BLUE, YELLOW, GREEN)")
public enum CommentColor {
    @Schema(description = "빨간색")
    RED,

    @Schema(description = "파란색")
    BLUE,

    @Schema(description = "노란색")
    YELLOW,

    @Schema(description = "초록색")
    GREEN;

    public static CommentColor fromString(String value) {
        if (value == null || value.isBlank()) {
            return BLUE;
        }
        try {
            return CommentColor.valueOf(value.trim().toUpperCase());
        } catch (Exception e) {
            return BLUE;
        }
    }
}
