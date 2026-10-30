package com.ceos24.autedi.domain.invitation.dto.response;

import com.ceos24.autedi.domain.invitation.entity.InvitationStatus;
import com.ceos24.autedi.domain.invitation.entity.UserInvitation;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "선생님 초대장 발송 결과 응답")
public record InvitationResponse(
        @Schema(description = "초대 레코드 고유 ID", example = "12") Long invitationId,
        @Schema(description = "초대받은 선생님 이메일", example = "teacher@autedi.com") String email,
        @Schema(description = "초대 상태 (PENDING: 수락 대기)", example = "PENDING") InvitationStatus status,
        @Schema(description = "초대 링크 만료 일시 (발송 시점 + 24시간)", example = "2026-10-31T18:00:00") LocalDateTime expiresAt) {
    public static InvitationResponse from(UserInvitation invitation) {
        return new InvitationResponse(
                invitation.getId(), invitation.getEmail(), invitation.getStatus(), invitation.getExpiresAt());
    }
}
