package com.ceos24.autedi.domain.correction.entity;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "캔버스 상의 바운딩 박스 영역 좌표")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RegionPayload(
        @Schema(description = "페이지 ID", example = "10") Long pageId,
        @Schema(description = "X 좌표 (px)", example = "120.5") Double x,
        @Schema(description = "Y 좌표 (px)", example = "340.0") Double y,
        @Schema(description = "가로 너비 (px)", example = "250.0") Double width,
        @Schema(description = "세로 높이 (px)", example = "45.0") Double height,
        @Schema(description = "다각형 꼭짓점 좌표 (선택)", example = "null") Object polygon) {
    public RegionPayload(Double x, Double y, Double width, Double height) {
        this(null, x, y, width, height, null);
    }
}
