package com.ceos24.autedi.domain.invitation.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "선생님 이메일 초대 요청")
public record InvitationCreateRequest(
        @Schema(description = "초대할 선생님의 수신 이메일 주소", example = "teacher@autedi.com")
                @NotBlank(message = "초대할 이메일은 필수 입력값입니다.")
                @Email(message = "올바른 이메일 형식이 아닙니다.")
                String email) {}
