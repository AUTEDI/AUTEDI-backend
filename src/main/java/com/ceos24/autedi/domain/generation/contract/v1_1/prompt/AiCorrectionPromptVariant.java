package com.ceos24.autedi.domain.generation.contract.v1_1.prompt;

/** v1.1 응답 계약을 사용하는 v1.2 프롬프트 후보. 리소스 경로는 계약 기준으로 유지한다. */
public enum AiCorrectionPromptVariant {
    DEFAULT("/ai/prompt/v1.1/", "AUTEDI-CORRECTION-PROMPT-v1.2", "AUTEDI-CORRECTION-PROMPT-v1.1"),
    COMPACT(
            "/ai/prompt/v1.1-compact/",
            "AUTEDI-CORRECTION-PROMPT-v1.2-COMPACT",
            "AUTEDI-CORRECTION-PROMPT-v1.1-COMPACT");

    private final String resourceRoot;
    private final String promptVersion;
    private final String legacyPromptVersion;

    AiCorrectionPromptVariant(String resourceRoot, String promptVersion, String legacyPromptVersion) {
        this.resourceRoot = resourceRoot;
        this.promptVersion = promptVersion;
        this.legacyPromptVersion = legacyPromptVersion;
    }

    String resourceRoot() {
        return resourceRoot;
    }

    public String promptVersion() {
        return promptVersion;
    }

    public String legacyPromptVersion() {
        return legacyPromptVersion;
    }
}
