package com.ceos24.autedi.domain.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "QR 세션 생성 요청")
public record QrSessionCreateRequest(
        @Schema(description = "사진을 올릴 대상 작업 ID. 요청자가 소유한 같은 학원의 작업이어야 합니다.", example = "12")
                @NotNull(message = "worksheetId는 필수입니다.")
                Long worksheetId) {}
