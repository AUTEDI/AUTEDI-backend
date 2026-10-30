package com.ceos24.autedi.domain.correction.dto.request;

import jakarta.validation.constraints.NotBlank;

public record CorrectionUpdateRequest(@NotBlank(message = "수정할 내용을 입력해 주세요.") String content) {}
