package com.ceos24.autedi.domain.generation.contract.v1;

/** 제안의 종류이며, REPLACE도 자동으로 원문을 교체하지 않는다. */
public enum AiAction {
    REPLACE,
    GUIDANCE,
    REVIEW
}
