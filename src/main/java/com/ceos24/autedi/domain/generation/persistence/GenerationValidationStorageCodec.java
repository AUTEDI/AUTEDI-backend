package com.ceos24.autedi.domain.generation.persistence;

import com.ceos24.autedi.domain.generation.contract.GenerationContractRouter;
import com.ceos24.autedi.domain.generation.contract.GenerationContractVersion;
import java.util.Map;
import java.util.Objects;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/** 기존 validation_result JSON 형태를 유지하고, 저장된 계약 버전으로만 역직렬화한다. 재검증하지 않는다. */
public final class GenerationValidationStorageCodec {
    private static final JsonMapper JSON = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();
    private static final TypeReference<Map<String, Object>> OBJECT = new TypeReference<>() {};

    private GenerationValidationStorageCodec() {}

    public static GenerationContractVersion version(GenerationResultMetadata metadata) {
        Objects.requireNonNull(metadata, "metadata");
        for (var version : GenerationContractVersion.values()) {
            if (!version.contractId().equals(metadata.contractVersion())) continue;
            String expected = version == GenerationContractVersion.V1
                    ? GenerationResultMetadata.VALIDATION_VERSION
                    : GenerationResultMetadata.VALIDATION_VERSION_V1_1;
            if (!expected.equals(metadata.validationVersion())) {
                throw new IllegalArgumentException("저장된 계약과 검증 버전을 지원하지 않습니다.");
            }
            return version;
        }
        throw new IllegalArgumentException("저장된 AI 계약 버전을 지원하지 않습니다.");
    }

    public static Map<String, Object> encode(
            GenerationResultMetadata metadata, GenerationContractRouter.Validation validation) {
        var version = version(metadata);
        if (validation == null) return null;
        Object result =
                switch (validation) {
                    case GenerationContractRouter.V1Validation v1 -> {
                        if (version != GenerationContractVersion.V1) throw mismatch();
                        yield Objects.requireNonNull(v1.result());
                    }
                    case GenerationContractRouter.V11Validation v11 -> {
                        if (version != GenerationContractVersion.V1_1) throw mismatch();
                        yield Objects.requireNonNull(v11.result());
                    }
                };
        return JSON.convertValue(result, OBJECT);
    }

    public static GenerationContractRouter.Validation decode(GenerationResultMetadata metadata, Object stored) {
        var version = version(metadata);
        if (stored == null) return null;
        return switch (version) {
            case V1 -> new GenerationContractRouter.V1Validation(JSON.convertValue(
                    stored, com.ceos24.autedi.domain.generation.contract.v1.validation.AiValidationResult.class));
            case V1_1 -> new GenerationContractRouter.V11Validation(JSON.convertValue(
                    stored, com.ceos24.autedi.domain.generation.contract.v1_1.validation.AiValidationResult.class));
        };
    }

    private static IllegalArgumentException mismatch() {
        return new IllegalArgumentException("저장 메타데이터와 검증 결과의 계약 버전이 다릅니다.");
    }
}
