package com.ceos24.autedi.domain.generation.contract.v1_1;

/** 제안의 용도. REPLACE의 세부 수정 동작은 AiEditOperation으로 구분한다. */
public enum AiAction {
    REPLACE,
    GUIDANCE,
    REVIEW
}
