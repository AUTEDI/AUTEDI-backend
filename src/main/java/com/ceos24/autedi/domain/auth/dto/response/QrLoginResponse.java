package com.ceos24.autedi.domain.auth.dto.response;

import com.ceos24.autedi.domain.user.dto.response.UserResponse;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "QR 로그인 성공 응답. 일반 로그인 응답에 사진을 올릴 대상 작업 ID가 추가됩니다.")
public record QrLoginResponse(
        @Schema(
                        description = "1시간 유효한 JWT Access Token. 이번 QR 세션에 연결되어 있습니다.",
                        example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
                String accessToken,
        @Schema(description = "14일 유효한 Refresh Token", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
                String refreshToken,
        @Schema(description = "로그인한 사용자 및 학원 정보") UserResponse user,
        @Schema(
                        description = "이 QR로 사진을 올릴 대상 작업 ID. PUT /api/v1/worksheets/{worksheetId}/file 에 사용합니다.",
                        example = "12")
                Long worksheetId) {

    public static QrLoginResponse of(LoginResponse login, Long worksheetId) {
        return new QrLoginResponse(login.accessToken(), login.refreshToken(), login.user(), worksheetId);
    }
}
