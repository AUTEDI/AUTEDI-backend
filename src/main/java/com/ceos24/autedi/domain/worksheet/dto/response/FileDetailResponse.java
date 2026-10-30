package com.ceos24.autedi.domain.worksheet.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "학습지 원본 파일 상세 정보")
public record FileDetailResponse(
        @Schema(description = "원본 파일명", example = "lesson1_homework.png") String name,
        @Schema(description = "MIME 미디어 타입", example = "image/png") String mediaType,
        @Schema(description = "파일 크기 (Byte)", example = "2458120") Long sizeBytes,
        @Schema(description = "스토리지 파일 접근 URL", example = "/uploads/2026/10/lesson1_homework.png") String originalUrl,
        @Schema(description = "서명된 URL 만료 시각 (S3 사용 시)", example = "null") LocalDateTime urlExpiresAt) {}
