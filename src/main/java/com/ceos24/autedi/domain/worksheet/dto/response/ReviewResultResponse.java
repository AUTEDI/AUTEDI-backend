package com.ceos24.autedi.domain.worksheet.dto.response;

import com.ceos24.autedi.domain.generation.persistence.GenerationResultMetadata;

/** FIXED는 실제 AI 분석 결과가 아닌 고정 예제임을 표시한다. 원본 응답 전문은 일반 검토 API에 노출하지 않는다. */
public record ReviewResultResponse(
        Long id,
        Long generationId,
        Long pageId,
        Long transcriptionId,
        String source,
        GenerationResultMetadata metadata) {}
