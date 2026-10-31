package com.ceos24.autedi.domain.generation.client;

import com.ceos24.autedi.domain.generation.contract.v1.AiCorrectionResponseParser;
import com.ceos24.autedi.domain.generation.contract.v1.prompt.AiCorrectionPromptComposer;
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
        return mode == GenerationClientMode.TERRA
                ? GenerationResultMetadata.current()
                : new GenerationResultMetadata(
                        model(mode),
                        AiCorrectionResponseParser.CONTRACT_ID,
                        FixedResponseGenerationService.PROMPT_VERSION,
                        GenerationResultMetadata.VALIDATION_VERSION);
    }
}
