package com.ceos24.autedi.domain.generation.contract.v1_1;

import com.fasterxml.jackson.annotation.JsonProperty;

/** 전사문 한 줄의 정확한 원문 후보. 여러 anchors의 연속성과 UTF-16 범위는 서버가 검증·계산한다. */
public record AiAnchor(@JsonProperty("line_id") int lineId, String original, int occurrence) {}
