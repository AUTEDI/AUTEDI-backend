package com.ceos24.autedi.domain.auth.dto.response;

import com.ceos24.autedi.domain.auth.entity.QrSessionStatus;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "QR 세션 상태 응답")
public record QrSessionStatusResponse(
        @Schema(description = "PENDING, SCANNED, APPROVED, COMPLETED, REJECTED, EXPIRED", example = "SCANNED")
                QrSessionStatus status) {}
