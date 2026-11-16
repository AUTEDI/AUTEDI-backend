package com.ceos24.autedi.domain.worksheet.dto.response;

import com.ceos24.autedi.domain.generation.contract.v1.AiArea;
import com.ceos24.autedi.domain.generation.contract.v1.validation.AiCorrectionLevel;

/** validatedCount는 최초 검증 당시 수, displayedCount는 현재 삭제되지 않은 카드 수다. */
public record ReviewAreaSummaryResponse(
        Long generationId,
        Long pageId,
        int pageNumber,
        AiArea area,
        AiCorrectionLevel level,
        int recommendedCount,
        int maximumCount,
        int validatedCount,
        int displayedCount,
        int omittedCount) {}
