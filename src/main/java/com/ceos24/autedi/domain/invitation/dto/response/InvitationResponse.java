package com.ceos24.autedi.domain.invitation.dto.response;

import com.ceos24.autedi.domain.invitation.entity.InvitationStatus;
import com.ceos24.autedi.domain.invitation.entity.UserInvitation;
import java.time.LocalDateTime;

public record InvitationResponse(
        Long invitationId,
        String email,
        InvitationStatus status,
        LocalDateTime expiresAt
) {
    public static InvitationResponse from(UserInvitation invitation) {
        return new InvitationResponse(
                invitation.getId(),
                invitation.getEmail(),
                invitation.getStatus(),
                invitation.getExpiresAt()
        );
    }
}
