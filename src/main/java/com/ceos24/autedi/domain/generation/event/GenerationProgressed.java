package com.ceos24.autedi.domain.generation.event;

import com.ceos24.autedi.domain.generation.entity.GenerationStage;

/** 작업 단계가 바뀌었을 때(OCR 완료 → AI_CORRECTION) 발행된다. */
public record GenerationProgressed(Long userId, Long worksheetId, Long generationId, GenerationStage stage) {}
