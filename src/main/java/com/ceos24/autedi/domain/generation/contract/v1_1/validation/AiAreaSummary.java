package com.ceos24.autedi.domain.generation.contract.v1_1.validation;

import com.ceos24.autedi.domain.generation.contract.v1_1.AiArea;

/** 최초 검증·제한 직후 집계. 교사 편집·삭제 이후의 displayedCount는 서비스 연결 시 별도 계산한다. */
public record AiAreaSummary(
        AiArea area,
        AiCorrectionLevel level,
        int maximumCount,
        int validatedCount,
        int displayedCount,
        int omittedCount) {}
