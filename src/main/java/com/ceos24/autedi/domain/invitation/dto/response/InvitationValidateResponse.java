package com.ceos24.autedi.domain.invitation.dto.response;

import java.time.LocalDateTime;

public record InvitationValidateResponse(boolean valid, String email, String academyName, LocalDateTime expiresAt) {}
