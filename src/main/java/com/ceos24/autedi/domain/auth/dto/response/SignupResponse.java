package com.ceos24.autedi.domain.auth.dto.response;

import com.ceos24.autedi.domain.user.entity.Role;
import com.ceos24.autedi.domain.user.entity.User;

public record SignupResponse(
        Long userId,
        String email,
        String name,
        Role role
) {
    public static SignupResponse from(User user) {
        return new SignupResponse(
                user.getId(),
                user.getEmail(),
                user.getName(),
                user.getRole()
        );
    }
}
