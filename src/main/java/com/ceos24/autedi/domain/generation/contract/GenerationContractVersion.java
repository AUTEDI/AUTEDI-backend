package com.ceos24.autedi.domain.generation.contract;

import java.util.Locale;

/** AI 계약 버전이다. REST API의 /api/v1 및 AI 공급자 선택과 독립적이다. */
public enum GenerationContractVersion {
    V1("v1", "AUTEDI-AI-CONTRACT-v1"),
    V1_1("v1.1", "AUTEDI-AI-CONTRACT-v1.1");

    private final String setting;
    private final String contractId;

    GenerationContractVersion(String setting, String contractId) {
        this.setting = setting;
        this.contractId = contractId;
    }

    public String setting() {
        return setting;
    }

    public String contractId() {
        return contractId;
    }

    public static GenerationContractVersion fromSetting(String value) {
        if (value != null) {
            String normalized = value.toLowerCase(Locale.ROOT);
            for (var version : values()) {
                if (version.setting.equals(normalized)) return version;
            }
        }
        throw new IllegalArgumentException("AI 첨삭 계약 버전은 v1 또는 v1.1이어야 합니다.");
    }
}
