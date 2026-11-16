package com.ceos24.autedi.domain.generation.contract.v1.validation;

/** field에는 boxes[2]처럼 원본 후보 안의 위치를 기록한다. */
public record AiValidationIssue(AiValidationCode code, String field, String message) {}
