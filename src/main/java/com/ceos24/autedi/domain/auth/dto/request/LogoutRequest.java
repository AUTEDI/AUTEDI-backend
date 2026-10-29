package com.ceos24.autedi.domain.auth.dto.request;

public record LogoutRequest(
        String refreshToken
) {
}
