package com.ceos24.autedi.domain.worksheet.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "학습지 개별 페이지 미리보기 정보")
public record PageDetailResponse(
        @Schema(description = "페이지 고유 ID", example = "10") Long id,
        @Schema(description = "페이지 번호 (1부터 시작)", example = "1") Integer pageNumber,
        @Schema(description = "페이지 가로 너비 (px)", example = "1240") Integer width,
        @Schema(description = "페이지 세로 높이 (px)", example = "1754") Integer height,
        @Schema(description = "미리보기 이미지 URL", example = "/previews/page_10.png") String imageUrl,
        @Schema(description = "미리보기 URL 만료 시각", example = "null") LocalDateTime urlExpiresAt) {}
