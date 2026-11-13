package com.ceos24.autedi.domain.correction.dto.response;

/** 버전별 검증 코드를 손실 없이 제공한다. 기존 code/field/message JSON 형태는 유지한다. */
public record CorrectionWarningResponse(String code, String field, String message) {
    public static CorrectionWarningResponse from(
            com.ceos24.autedi.domain.generation.contract.v1.validation.AiValidationIssue issue) {
        return new CorrectionWarningResponse(issue.code().name(), issue.field(), issue.message());
    }

    public static CorrectionWarningResponse from(
            com.ceos24.autedi.domain.generation.contract.v1_1.validation.AiValidationIssue issue) {
        return new CorrectionWarningResponse(issue.code().name(), issue.field(), issue.message());
    }
}
