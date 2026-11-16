package com.ceos24.autedi.domain.generation.contract.v1_1.validation;

/** 서버가 확인한 한 줄 안의 0 기반 UTF-16 범위. end는 미포함이며 서로게이트 쌍을 자르지 않는다. */
public record AiResolvedAnchor(int lineId, int start, int end) {}
