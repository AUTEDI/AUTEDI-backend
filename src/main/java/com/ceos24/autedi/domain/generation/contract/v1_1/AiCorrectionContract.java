package com.ceos24.autedi.domain.generation.contract.v1_1;

/** v1.1 형식의 식별 정보. 실제 요청·파싱·검증 경로의 버전 선택은 별도로 연결한다. */
public final class AiCorrectionContract {
    public static final String CONTRACT_ID = "AUTEDI-AI-CONTRACT-v1.1";
    public static final String SCHEMA_RESOURCE = "/ai/contract/v1.1/correction-response.schema.json";

    private AiCorrectionContract() {}
}
