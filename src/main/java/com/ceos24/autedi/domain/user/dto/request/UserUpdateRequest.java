package com.ceos24.autedi.domain.user.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "내 정보(이름) 수정 요청")
public record UserUpdateRequest(
        @Schema(description = "변경할 이름 (앞뒤 공백은 제거되어 저장)", example = "김민수")
                @NotBlank(message = "이름은 필수 입력값입니다.")
                @Size(max = 50, message = "이름은 50자 이내여야 합니다.")
                String name) {}
