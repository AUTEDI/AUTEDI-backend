package com.ceos24.autedi.domain.generation.dto.response;

public record GenerationErrorResponse(String code, String message, Boolean retryable) {}
