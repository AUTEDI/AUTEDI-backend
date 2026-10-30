package com.ceos24.autedi.domain.auth.dto.response;

import com.ceos24.autedi.domain.user.entity.Role;
import com.ceos24.autedi.domain.user.entity.User;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "선생님 회원가입 완료 응답")
public record SignupResponse(
        @Schema(description = "생성된 회원 고유 ID", example = "45") Long userId,
        @Schema(description = "가입된 이메일 계정", example = "teacher@autedi.com") String email,
        @Schema(description = "회원 이름", example = "김선생") String name,
        @Schema(description = "부여된 역할 권한 (항상 TEACHER)", example = "TEACHER") Role role) {
    public static SignupResponse from(User user) {
        return new SignupResponse(user.getId(), user.getEmail(), user.getName(), user.getRole());
    }
}
