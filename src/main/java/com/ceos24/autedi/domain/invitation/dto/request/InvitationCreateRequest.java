package com.ceos24.autedi.domain.invitation.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "선생님 이메일 초대 요청")
public record InvitationCreateRequest(
        @Schema(description = "초대할 선생님 이름 (앞뒤 공백은 제거되어 저장)", example = "김선생")
                @NotBlank(message = "초대할 선생님 이름은 필수 입력값입니다.")
                @Size(max = 50, message = "이름은 50자 이내여야 합니다.")
                String name,
        @Schema(description = "초대할 선생님의 수신 이메일 주소", example = "teacher@autedi.com")
                @NotBlank(message = "초대할 이메일은 필수 입력값입니다.")
                @Email(message = "올바른 이메일 형식이 아닙니다.")
                String email) {}
