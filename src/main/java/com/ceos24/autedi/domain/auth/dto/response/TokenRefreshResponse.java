package com.ceos24.autedi.domain.auth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "토큰 재발급(RTR) 성공 응답")
public record TokenRefreshResponse(
        @Schema(description = "새롭게 갱신된 1시간 유효 JWT Access Token", example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
                String accessToken,
        @Schema(
                        description = "새롭게 회전(Rotation) 발급된 14일 유효 Refresh Token",
                        example = "8e0c5d1f-g543-5e90-c865-2c4b35f8b90a")
                String refreshToken) {}
