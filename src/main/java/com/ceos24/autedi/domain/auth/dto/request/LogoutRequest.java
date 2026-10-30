package com.ceos24.autedi.domain.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "로그아웃 요청")
public record LogoutRequest(
        @Schema(description = "폐기할 기기의 Refresh Token (선택)", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
                String refreshToken) {}
