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

    /** 선택 결과를 만든다. 독립 라우터/준비 상태 확인에 사용하며 서비스 실행 가능 여부와 구분한다. */
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
        // 11~14단계에서 v1.1 저장·조회·편집·Worker 연결이 끝난 뒤 실행 지원 범위를 확장한다.
        if (profile.version() != GenerationContractVersion.V1) {
            throw new IllegalArgumentException("v1.1 서비스 저장·생성 경로는 아직 연결 전입니다.");
        }
    }
}
