package com.ceos24.autedi.domain.generation.contract;

import com.ceos24.autedi.domain.generation.contract.v1_1.prompt.AiCorrectionPromptVariant;
import com.ceos24.autedi.domain.generation.persistence.GenerationResultMetadata;
import java.util.Objects;

/** 접수 당시의 불변 실행 프로필. 결과 보존·복원용 메타데이터와 같은 버전을 사용한다. */
public record GenerationAiProfile(
        GenerationContractVersion version, AiCorrectionPromptVariant promptVariant, GenerationResultMetadata metadata) {
    public GenerationAiProfile {
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(promptVariant, "promptVariant");
        Objects.requireNonNull(metadata, "metadata");
        if (!version.contractId().equals(metadata.contractVersion())
                || version == GenerationContractVersion.V1 && promptVariant != AiCorrectionPromptVariant.DEFAULT) {
            throw new IllegalArgumentException("계약 버전·프롬프트 후보·메타데이터가 일치하지 않습니다.");
        }
    }
}
