package com.ceos24.autedi.domain.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "QR 세션 생성 요청 (body 생략 가능)")
public record QrSessionCreateRequest(
        @Schema(
                        description = "기존 작업의 사진을 교체할 때만 지정합니다(요청자가 소유한 같은 학원의 작업). "
                                + "첫 사진이라 아직 작업이 없으면 비우거나 null로 보내며, 이 경우 휴대폰이 올린 사진으로 새 작업이 만들어집니다.",
                        example = "12")
                Long worksheetId) {}
