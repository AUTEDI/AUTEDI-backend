package com.ceos24.autedi.domain.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "QR 세션 생성 요청 (body 생략 가능)")
public record QrSessionCreateRequest(
        @Schema(description = "기존 작업의 사진을 교체할 때만 지정합니다. 새 작업이면 null", example = "null") Long worksheetId) {}
