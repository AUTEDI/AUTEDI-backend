package com.ceos24.autedi.domain.generation.event;

/** 워커가 첨삭 작업을 시작(PENDING→RUNNING)했을 때 발행된다. userId는 수신자(학습지 소유자). */
public record GenerationStarted(Long userId, Long worksheetId, Long generationId) {}
