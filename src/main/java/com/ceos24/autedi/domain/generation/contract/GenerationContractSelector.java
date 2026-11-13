package com.ceos24.autedi.domain.generation.contract;

import com.ceos24.autedi.domain.generation.client.GenerationClientProfiles;
import com.ceos24.autedi.domain.generation.contract.v1_1.prompt.AiCorrectionPromptVariant;
import com.ceos24.autedi.domain.generation.entity.GenerationClientMode;
import com.ceos24.autedi.domain.generation.entity.WorksheetGeneration;
import com.ceos24.autedi.global.exception.CustomException;
import com.ceos24.autedi.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class GenerationContractSelector {
    private final GenerationContractProperties properties;

    /** 새 작업의 프로필을 선택한다. 재시도와 실행은 저장된 프로필을 사용한다. */
    public GenerationAiProfile configuredProfile(GenerationClientMode mode) {
        return GenerationClientProfiles.profile(mode, properties.version(), properties.variant());
    }

    public GenerationAiProfile forGeneration(GenerationClientMode mode, WorksheetGeneration previous) {
        try {
            GenerationAiProfile selected = previous == null ? configuredProfile(mode) : snapshot(mode, previous);
            requireServiceReady(mode, selected);
            return selected;
        } catch (IllegalArgumentException e) {
            throw new CustomException(ErrorCode.AI_CONTRACT_NOT_READY);
        }
    }

    /** 프로필 없는 기존 작업만 v1로 해석한다. 현재 서버 선택 설정으로 재해석하지 않는다. */
    public static GenerationAiProfile snapshot(WorksheetGeneration job) {
        return snapshot(job.getClientMode(), job);
    }

    private static GenerationAiProfile snapshot(GenerationClientMode mode, WorksheetGeneration job) {
        GenerationAiProfile saved = job.getRequestAiProfile();
        return saved != null
                ? saved
                : GenerationClientProfiles.profile(
                        mode, GenerationContractVersion.V1, AiCorrectionPromptVariant.DEFAULT);
    }

    public static void requireServiceReady(GenerationClientMode mode, GenerationAiProfile profile) {
        GenerationClientProfiles.requireKnown(mode, profile);
    }
}
