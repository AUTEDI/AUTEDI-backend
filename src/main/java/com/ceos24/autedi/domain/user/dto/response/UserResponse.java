package com.ceos24.autedi.domain.user.dto.response;

import com.ceos24.autedi.domain.academy.entity.SubscriptionStatus;
import com.ceos24.autedi.domain.user.entity.Role;
import com.ceos24.autedi.domain.user.entity.User;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "사용자 프로필 및 소속 학원 세션 정보")
public record UserResponse(
        @Schema(description = "회원 고유 ID", example = "45") Long userId,
        @Schema(description = "회원 이메일", example = "teacher@autedi.com") String email,
        @Schema(description = "회원 실명", example = "김선생") String name,
        @Schema(description = "회원 권한 (DIRECTOR: 원장, TEACHER: 교사)", example = "TEACHER") Role role,
        @Schema(description = "소속 학원 ID", example = "1") Long academyId,
        @Schema(description = "소속 학원명", example = "에이플러스 학원") String academyName,
        @Schema(description = "학원 이용권 구독 상태 (ACTIVE, EXPIRED, SUSPENDED)", example = "ACTIVE")
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
