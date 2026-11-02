package com.ceos24.autedi.domain.generation.contract.v1;

import com.fasterxml.jackson.annotation.JsonProperty;

/** 전사문 한 줄 안의 원문 후보. 실제 전사문 일치 여부와 UTF-16 위치는 후처리에서 확인한다. */
public record AiAnchor(@JsonProperty("line_id") int lineId, String original, int occurrence) {}
