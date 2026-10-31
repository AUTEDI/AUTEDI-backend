package com.ceos24.autedi.domain.generation.entity;

/** 접수 당시 실행 경로를 고정한다. null인 과거 작업은 새 워커가 자동 실행하지 않는다. */
public enum GenerationClientMode {
    LEGACY_MOCK,
    FIXED,
    TERRA
}
