package com.ceos24.autedi.domain.auth.dto.response;

import com.ceos24.autedi.domain.user.dto.response.UserResponse;

public record LoginResponse(String accessToken, String refreshToken, UserResponse user) {}
