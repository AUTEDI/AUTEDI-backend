package com.ceos24.autedi.domain.generation.client;

import com.ceos24.autedi.domain.generation.contract.GenerationAiProfile;
import com.ceos24.autedi.domain.generation.contract.GenerationContractVersion;
import com.ceos24.autedi.domain.generation.contract.v1.AiCorrectionResponseParser;
import com.ceos24.autedi.domain.generation.contract.v1.prompt.AiCorrectionPromptComposer;
import com.ceos24.autedi.domain.generation.contract.v1_1.prompt.AiCorrectionPromptVariant;
import com.ceos24.autedi.domain.generation.entity.GenerationClientMode;
import com.ceos24.autedi.domain.generation.persistence.GenerationResultMetadata;
import com.ceos24.autedi.domain.generation.service.FixedResponseGenerationService;

public final class GenerationClientProfiles {
    private GenerationClientProfiles() {}

    public static String model(GenerationClientMode mode) {
        return mode == GenerationClientMode.TERRA
                ? AiCorrectionPromptComposer.MODEL
                : FixedResponseGenerationService.MODEL;
    }

    public static GenerationResultMetadata metadata(GenerationClientMode mode) {
        return profile(mode, GenerationContractVersion.V1, AiCorrectionPromptVariant.DEFAULT)
                .metadata();
    }

    public static GenerationAiProfile profile(
            GenerationClientMode mode, GenerationContractVersion version, AiCorrectionPromptVariant variant) {
        java.util.Objects.requireNonNull(mode, "mode");
        java.util.Objects.requireNonNull(version, "version");
        java.util.Objects.requireNonNull(variant, "variant");
        if (version == GenerationContractVersion.V1) {
            return new GenerationAiProfile(
                    version,
                    variant,
                    new GenerationResultMetadata(
                            model(mode),
                            AiCorrectionResponseParser.CONTRACT_ID,
                            mode == GenerationClientMode.TERRA
                                    ? AiCorrectionPromptComposer.PROMPT_VERSION
                                    : FixedResponseGenerationService.PROMPT_VERSION,
                            GenerationResultMetadata.VALIDATION_VERSION));
        }
        if (mode != GenerationClientMode.TERRA) {
            throw new IllegalArgumentException("v1.1 고정/레거시 생성 클라이언트는 아직 연결하지 않았습니다.");
        }
        return new GenerationAiProfile(
                version,
                variant,
                new GenerationResultMetadata(
                        com.ceos24.autedi.domain.generation.contract.v1_1.prompt.AiCorrectionPromptComposer.MODEL,
                        version.contractId(),
                        variant.promptVersion(),
                        GenerationResultMetadata.VALIDATION_VERSION_V1_2));
    }

    /** 명시적으로 지원하는 과거 v1.1 프로필만 같은 계약·모델·후보의 v1.2 실행 프로필로 전환한다. */
    public static GenerationAiProfile forExecution(GenerationClientMode mode, GenerationAiProfile saved) {
        java.util.Objects.requireNonNull(saved, "saved");
        GenerationAiProfile current = profile(mode, saved.version(), saved.promptVariant());
        if (current.equals(saved)) return saved;
        var legacyMetadata = new GenerationResultMetadata(
                current.metadata().model(),
                current.metadata().contractVersion(),
                saved.promptVariant().legacyPromptVersion(),
                GenerationResultMetadata.VALIDATION_VERSION_V1_1);
        if (saved.version() == GenerationContractVersion.V1_1 && legacyMetadata.equals(saved.metadata())) {
            return current;
        }
        throw new IllegalArgumentException("저장된 AI 실행 프로필을 현재 코드에서 정확히 실행할 수 없습니다.");
    }

    /** 실제 프롬프트·검증기 호출에는 현재 실행 프로필만 허용한다. */
    public static void requireKnown(GenerationClientMode mode, GenerationAiProfile profile) {
        java.util.Objects.requireNonNull(profile, "profile");
        if (!profile(mode, profile.version(), profile.promptVariant()).equals(profile)) {
            throw new IllegalArgumentException("저장된 AI 실행 프로필을 현재 코드에서 정확히 실행할 수 없습니다.");
        }
    }
}
