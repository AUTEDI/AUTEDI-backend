package com.ceos24.autedi.domain.user.dto.response;

import com.ceos24.autedi.domain.academy.entity.SubscriptionStatus;
import com.ceos24.autedi.domain.user.entity.Role;
import com.ceos24.autedi.domain.user.entity.User;

public record UserResponse(
        Long userId,
        String email,
        String name,
        Role role,
        Long academyId,
        String academyName,
        SubscriptionStatus subscriptionStatus) {
    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getName(),
                user.getRole(),
                user.getAcademy().getId(),
                user.getAcademy().getName(),
                user.getAcademy().getSubscriptionStatus());
    }
}
