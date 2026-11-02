package com.ceos24.autedi.domain.generation.contract.v1.validation;

import com.ceos24.autedi.domain.generation.contract.v1.AiArea;

/** 선택하지 않은 영역은 level=null, 권장·최대 수는 0이다. omittedCount에는 제한 초과 외의 제외도 포함한다. */
public record AiAreaSummary(
        AiArea area,
        AiCorrectionLevel level,
        int recommendedCount,
        int maximumCount,
        int displayedCount,
        int omittedCount) {}
