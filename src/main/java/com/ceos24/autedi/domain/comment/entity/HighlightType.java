package com.ceos24.autedi.domain.comment.entity;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "선생님 캔버스 강조/표시 유형 (HIGHLIGHT: 형광펜, UNDERLINE: 밑줄, ARROW: 화살표, TEXTBOX: 텍스트 상자, CARET: 삽입 기호)")
public enum HighlightType {
    @Schema(description = "형광펜 하이라이트")
    HIGHLIGHT,

    @Schema(description = "밑줄")
    UNDERLINE,

    @Schema(description = "화살표 표시")
    ARROW,

    @Schema(description = "텍스트 상자")
    TEXTBOX,

    @Schema(description = "삽입 기호 (Caret ^)")
    CARET
}
