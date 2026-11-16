package com.ceos24.autedi.domain.generation.contract;

import com.ceos24.autedi.domain.generation.contract.v1_1.prompt.AiCorrectionPromptVariant;
import java.util.Locale;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.ai.correction")
public record GenerationContractProperties(String contractVersion, String promptVariant) {
    public GenerationContractProperties {
        contractVersion = GenerationContractVersion.fromSetting(contractVersion == null ? "v1" : contractVersion)
                .setting();
        promptVariant = (promptVariant == null ? "DEFAULT" : promptVariant).toUpperCase(Locale.ROOT);
        AiCorrectionPromptVariant variant = AiCorrectionPromptVariant.valueOf(promptVariant);
        if (contractVersion.equals("v1") && variant != AiCorrectionPromptVariant.DEFAULT) {
            throw new IllegalArgumentException("v1은 DEFAULT 프롬프트만 지원합니다.");
        }
    }

    public GenerationContractVersion version() {
        return GenerationContractVersion.fromSetting(contractVersion);
    }

    public AiCorrectionPromptVariant variant() {
        return AiCorrectionPromptVariant.valueOf(promptVariant);
    }
}
