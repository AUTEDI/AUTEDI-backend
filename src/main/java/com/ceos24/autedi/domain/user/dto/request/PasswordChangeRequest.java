package com.ceos24.autedi.domain.user.dto.request;

import com.ceos24.autedi.global.validation.ValidPassword;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "비밀번호 변경 요청")
public record PasswordChangeRequest(
        @Schema(description = "현재 비밀번호", example = "OldPassword123!") @NotBlank(message = "현재 비밀번호는 필수 입력값입니다.")
                String currentPassword,
        @Schema(description = "새 비밀번호 (8~20자, 영문·숫자·특수문자 각 1개 이상 포함)", example = "NewPassword456!")
                @NotBlank(message = "새 비밀번호는 필수 입력값입니다.")
                @ValidPassword
                String newPassword) {}
