package com.ceos24.autedi.domain.worksheet.dto.response;

import java.time.LocalDateTime;

public record PageDetailResponse(
        Long id, Integer pageNumber, Integer width, Integer height, String imageUrl, LocalDateTime urlExpiresAt) {}
