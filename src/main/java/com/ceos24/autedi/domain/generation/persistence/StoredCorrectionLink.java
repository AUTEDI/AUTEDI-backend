package com.ceos24.autedi.domain.generation.persistence;

/** 원본 items의 0 기반 인덱스와 편집 가능한 Correction의 연결. */
public record StoredCorrectionLink(int candidateIndex, Long correctionId) {}
