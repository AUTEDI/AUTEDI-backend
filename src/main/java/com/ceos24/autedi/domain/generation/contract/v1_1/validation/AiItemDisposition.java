package com.ceos24.autedi.domain.generation.contract.v1_1.validation;

/** CARD에는 CORRECTION과 SUGGESTION이 모두 포함된다. 원본 action·feedback_type은 변경하지 않는다. */
public enum AiItemDisposition {
    CARD,
    REVIEW,
    EXCLUDED
}
