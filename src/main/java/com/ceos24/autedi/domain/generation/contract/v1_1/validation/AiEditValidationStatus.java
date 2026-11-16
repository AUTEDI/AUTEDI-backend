package com.ceos24.autedi.domain.generation.contract.v1_1.validation;

/** 이 단계의 일관성만 표현하며 정상 카드·레벨 정책 승인 여부를 뜻하지 않는다. */
public enum AiEditValidationStatus {
    CONSISTENT,
    INCONSISTENT,
    SOURCE_UNRESOLVED
}
