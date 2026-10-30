package com.ceos24.autedi.domain.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "선생님 회원가입 요청")
public record SignupRequest(
        @Schema(description = "이메일로 수신된 256-bit 초대 원본 토큰", example = "9b1deb4d3b7d4e89acbf123456789abc")
                @NotBlank(message = "초대 토큰은 필수 입력값입니다.")
                String token,
        @Schema(description = "초대받은 선생님 이메일 (초대장 이메일과 일치해야 함)", example = "teacher@autedi.com")
                @NotBlank(message = "이메일은 필수 입력값입니다.")
                @Email(message = "올바른 이메일 형식이 아닙니다.")
                String email,
        @Schema(description = "선생님 실명/이름", example = "김선생")
                @NotBlank(message = "이름은 필수 입력값입니다.")
                @Size(max = 50, message = "이름은 50자 이내여야 합니다.")
                String name,
        @Schema(description = "로그인 비밀번호 (8자 이상)", example = "Password123!")
                @NotBlank(message = "비밀번호는 필수 입력값입니다.")
                @Size(min = 8, message = "비밀번호는 최소 8자 이상이어야 합니다.")
                String password) {}
