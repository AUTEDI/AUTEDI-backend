package com.ceos24.autedi.domain.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "로그인 요청")
public record LoginRequest(
        @Schema(description = "회원 이메일 주소", example = "director@autedi.com")
                @NotBlank(message = "이메일은 필수 입력값입니다.")
                @Email(message = "올바른 이메일 형식이 아닙니다.")
                String email,
        @Schema(description = "계정 비밀번호", example = "Password123!") @NotBlank(message = "비밀번호는 필수 입력값입니다.")
                String password) {}
