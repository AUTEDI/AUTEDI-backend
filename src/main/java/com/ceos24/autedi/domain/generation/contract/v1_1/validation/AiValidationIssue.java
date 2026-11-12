package com.ceos24.autedi.domain.generation.contract.v1_1.validation;

/** field는 anchors[1].original 또는 target_boxes[0]처럼 원본 후보 안의 위치를 가리킨다. */
public record AiValidationIssue(AiValidationCode code, String field, String message) {}
