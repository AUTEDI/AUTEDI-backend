package com.ceos24.autedi.domain.correction.entity;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "조회된 페이지 이미지 전체 기준 0~1 정규화 영역. 페이지 width/height(px)와 좌표 단위를 구분합니다.")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RegionPayload(
        @Schema(description = "페이지 ID", example = "10") Long pageId,
        @Schema(description = "좌상단 X (0~1)", example = "0.12") Double x,
        @Schema(description = "좌상단 Y (0~1)", example = "0.34") Double y,
        @Schema(description = "가로 비율 (0~1), right가 아닌 right-left", example = "0.25") Double width,
        @Schema(description = "세로 비율 (0~1), bottom이 아닌 bottom-top", example = "0.045") Double height,
        @Schema(description = "다각형 꼭짓점 좌표 (선택, 이 단계의 이미지 연동에서는 사용하지 않음)", example = "null") Object polygon) {
    public RegionPayload(Double x, Double y, Double width, Double height) {
        this(null, x, y, width, height, null);
    }
}
