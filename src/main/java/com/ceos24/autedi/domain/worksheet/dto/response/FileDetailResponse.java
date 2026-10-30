package com.ceos24.autedi.domain.worksheet.dto.response;

import java.time.LocalDateTime;

public record FileDetailResponse(
        String name, String mediaType, Long sizeBytes, String originalUrl, LocalDateTime urlExpiresAt) {}
