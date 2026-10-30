package com.ceos24.autedi.domain.invitation.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "초대 링크 토큰 검증 응답")
public record InvitationValidateResponse(
        @Schema(description = "토큰 유효 여부", example = "true") boolean valid,
        @Schema(description = "초대 대상 이메일 주소 (가입 폼에 읽기 전용 자동 바인딩)", example = "teacher@autedi.com") String email,
        @Schema(description = "초대한 소속 학원명", example = "에이플러스 학원") String academyName,
        @Schema(description = "초대 링크 만료 일시", example = "2026-10-31T18:00:00") LocalDateTime expiresAt) {}
