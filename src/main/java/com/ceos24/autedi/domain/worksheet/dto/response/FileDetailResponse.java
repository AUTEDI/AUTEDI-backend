package com.ceos24.autedi.domain.worksheet.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "학습지 원본 파일 상세 정보")
public record FileDetailResponse(
        @Schema(description = "원본 파일명", example = "lesson1_homework.png") String name,
        @Schema(description = "MIME 미디어 타입", example = "image/png") String mediaType,
        @Schema(description = "파일 크기 (Byte)", example = "2458120") Long sizeBytes,
        @Schema(
                        description = "업로드 당시 바이트를 보존한 원본 다운로드 URL. Bearer 인증이 필요하며 화면 표시는 pages.imageUrl을 사용합니다.",
                        example = "/api/v1/worksheets/1/file")
                String originalUrl,
        @Schema(description = "서명된 URL 만료 시각 (S3 사용 시)", example = "null") LocalDateTime urlExpiresAt) {}
