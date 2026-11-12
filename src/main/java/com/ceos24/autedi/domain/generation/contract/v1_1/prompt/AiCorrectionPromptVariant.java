package com.ceos24.autedi.domain.generation.contract.v1_1.prompt;

/** 응답 계약은 같고 지시문 리소스·프롬프트 버전만 다른 두 비교 후보. */
public enum AiCorrectionPromptVariant {
    DEFAULT("/ai/prompt/v1.1/", "AUTEDI-CORRECTION-PROMPT-v1.1"),
    COMPACT("/ai/prompt/v1.1-compact/", "AUTEDI-CORRECTION-PROMPT-v1.1-COMPACT");

    private final String resourceRoot;
    private final String promptVersion;

    AiCorrectionPromptVariant(String resourceRoot, String promptVersion) {
        this.resourceRoot = resourceRoot;
        this.promptVersion = promptVersion;
    }

    String resourceRoot() {
        return resourceRoot;
    }

    public String promptVersion() {
        return promptVersion;
    }
}
