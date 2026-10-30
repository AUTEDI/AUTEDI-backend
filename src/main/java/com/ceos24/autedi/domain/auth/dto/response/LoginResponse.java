package com.ceos24.autedi.domain.auth.dto.response;

import com.ceos24.autedi.domain.user.dto.response.UserResponse;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "로그인 성공 응답")
public record LoginResponse(
        @Schema(
                        description = "1시간 유효한 JWT Access Token (모든 인증 API 요청 헤더에 Authorization: Bearer <token> 으로 전송)",
                        example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
                String accessToken,
        @Schema(
                        description = "14일 유효한 Refresh Token (토큰 만료 시 재발급 API에서 사용)",
                        example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
                String refreshToken,
        @Schema(description = "로그인한 사용자 및 학원 정보") UserResponse user) {}
