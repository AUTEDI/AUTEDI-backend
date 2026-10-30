package com.ceos24.autedi.domain.comment.dto.request;

import jakarta.validation.constraints.NotBlank;

public record CommentUpdateRequest(@NotBlank(message = "코멘트 내용을 입력해 주세요.") String content) {}
