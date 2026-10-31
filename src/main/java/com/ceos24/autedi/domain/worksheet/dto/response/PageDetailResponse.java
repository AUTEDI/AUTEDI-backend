package com.ceos24.autedi.domain.worksheet.dto.response;

import com.ceos24.autedi.domain.correction.entity.RegionCoordinateUnit;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "학습지 개별 페이지 미리보기 정보")
public record PageDetailResponse(
        @Schema(description = "페이지 고유 ID", example = "10") Long id,
        @Schema(description = "페이지 번호 (1부터 시작)", example = "1") Integer pageNumber,
        @Schema(description = "EXIF 보정 PNG의 실제 가로 픽셀 수", example = "1240") Integer width,
        @Schema(description = "EXIF 보정 PNG의 실제 세로 픽셀 수", example = "1754") Integer height,
        @Schema(
                        description = "보정 PNG 조회 URL. Bearer 인증 후 image/png 바이트로 조회하며 전사·첨삭 입력과 같은 파일을 사용합니다.",
                        example = "/api/v1/worksheets/1/pages/1/image")
                String imageUrl,
        @Schema(description = "미리보기 URL 만료 시각", example = "null") LocalDateTime urlExpiresAt,
        @Schema(description = "regions의 x/y/width/height 수치 단위. 페이지 픽셀 크기와 별개입니다.", example = "NORMALIZED_0_1")
                RegionCoordinateUnit regionCoordinateUnit) {}
