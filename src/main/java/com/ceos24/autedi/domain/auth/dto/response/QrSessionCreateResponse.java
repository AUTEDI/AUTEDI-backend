package com.ceos24.autedi.domain.auth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "QR 세션 생성 응답")
public record QrSessionCreateResponse(
        @Schema(description = "노트북용 세션 ID (승인/거절 및 이벤트 구분에 사용)", example = "3f1c2b8e-7a41-4f0e-9c52-0d2b6a1e8f77")
                String sessionId,
        @Schema(description = "QR 만료 시각", example = "2026-11-11T14:35:00") LocalDateTime expiresAt,
        @Schema(
                        description = "<img src>에 바로 쓸 수 있는 base64 PNG data URI",
                        example = "data:image/png;base64,iVBORw0KGgo...")
                String qrImage) {}
