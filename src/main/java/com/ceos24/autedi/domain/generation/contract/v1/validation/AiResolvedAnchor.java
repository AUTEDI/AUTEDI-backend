package com.ceos24.autedi.domain.generation.contract.v1.validation;

/** 줄 번호는 1부터, start/end는 해당 줄 안의 UTF-16 인덱스이며 end는 미포함이다. */
public record AiResolvedAnchor(int lineId, int start, int end) {}
