package com.ceos24.autedi.domain.generation.contract.v1_1.validation;

/** v1.1 레벨은 허용 제안 유형을 구분한다. 두 레벨 모두 최대 7개이며 권장·최소 개수는 없다. */
public enum AiCorrectionLevel {
    STANDARD,
    ADVANCED;

    public int maximumCount() {
        return 7;
    }
}
