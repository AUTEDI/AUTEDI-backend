package com.ceos24.autedi.domain.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Access Token 재발급 요청")
public record TokenRefreshRequest(
        @Schema(description = "기존에 발급받은 유효한 Refresh Token 원본", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
                @NotBlank(message = "리프레시 토큰은 필수 입력값입니다.")
                String refreshToken) {}
