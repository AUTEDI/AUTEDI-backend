package com.ceos24.autedi.domain.generation.contract.v1_1.validation;

/** 현재는 원문·좌표 단계의 진단 코드다. 다른 검증 정책은 해당 단계 구현 시 확장한다. */
public enum AiValidationCode {
    ANCHORS_REQUIRED,
    ANCHOR_NOT_FOUND,
    ANCHORS_NOT_CONTIGUOUS,
    ANCHOR_BOUNDARY_MISMATCH,
    PARAGRAPH_BOUNDARY,
    ANCHOR_SPLITS_SURROGATE_PAIR,
    INVALID_TARGET_BOX,
    BOX_WITHOUT_ANCHORS,
    NO_VALID_TARGET_BOXES,
    MODEL_LOCATION_UNCERTAIN,
    LOCATION_FLAG_INCONSISTENT
}
