package com.ceos24.autedi.domain.generation.contract;

import com.ceos24.autedi.domain.generation.client.GenerationClientProfiles;
import com.ceos24.autedi.domain.generation.contract.v1.AiArea;
import com.ceos24.autedi.domain.generation.contract.v1.validation.AiCorrectionLevel;
import com.ceos24.autedi.domain.generation.contract.v1.validation.AiValidationContext;
import com.ceos24.autedi.domain.generation.entity.GenerationClientMode;
import com.ceos24.autedi.domain.generation.service.FixedResponseGenerationService;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** HTTP·저장·프론트 DTO 변환을 하지 않는 계약별 프롬프트/파서/검증기 선택 경계. */
@Component
public final class GenerationContractRouter {
    /** 현재 서비스의 설정 enum을 입력 경계에서만 사용하고, v1.1 실행 시 이름으로 명시적으로 변환한다. */
    public record Input(int pageNumber, List<String> lines, Map<AiArea, AiCorrectionLevel> settings) {
        public Input {
            var checked = new AiValidationContext(pageNumber, lines, settings);
            lines = checked.transcriptionLines();
            settings = checked.selectedAreas();
        }

        private AiValidationContext v1() {
            return new AiValidationContext(pageNumber, lines, settings);
        }

        private com.ceos24.autedi.domain.generation.contract.v1_1.validation.AiValidationContext v11() {
            var converted = new EnumMap<
                    com.ceos24.autedi.domain.generation.contract.v1_1.AiArea,
                    com.ceos24.autedi.domain.generation.contract.v1_1.validation.AiCorrectionLevel>(
                    com.ceos24.autedi.domain.generation.contract.v1_1.AiArea.class);
            settings.forEach((area, level) -> converted.put(
                    com.ceos24.autedi.domain.generation.contract.v1_1.AiArea.valueOf(area.name()),
                    com.ceos24.autedi.domain.generation.contract.v1_1.validation.AiCorrectionLevel.valueOf(
                            level.name())));
            return new com.ceos24.autedi.domain.generation.contract.v1_1.validation.AiValidationContext(
                    pageNumber, lines, converted);
        }
    }

    public record Prompt(GenerationAiProfile profile, String instructions, String inputText, String responseSchema) {}

    public sealed interface Validation permits V1Validation, V11Validation {}

    public record V1Validation(com.ceos24.autedi.domain.generation.contract.v1.validation.AiValidationResult result)
            implements Validation {}

    public record V11Validation(com.ceos24.autedi.domain.generation.contract.v1_1.validation.AiValidationResult result)
            implements Validation {}

    public Prompt compose(GenerationAiProfile profile, Input input) {
        GenerationClientProfiles.requireKnown(GenerationClientMode.TERRA, profile);
        return switch (profile.version()) {
            case V1 -> {
                var prompt = new com.ceos24.autedi.domain.generation.contract.v1.prompt.AiCorrectionPromptComposer()
                        .compose(input.v1());
                yield new Prompt(profile, prompt.instructions(), prompt.inputText(), prompt.responseSchema());
            }
            case V1_1 -> {
                var prompt = new com.ceos24.autedi.domain.generation.contract.v1_1.prompt.AiCorrectionPromptComposer()
                        .compose(input.v11(), profile.promptVariant());
                yield new Prompt(profile, prompt.instructions(), prompt.inputText(), prompt.responseSchema());
            }
        };
    }

    public Validation parseAndValidate(GenerationAiProfile profile, String raw, Input input) {
        GenerationClientProfiles.requireKnown(
                FixedResponseGenerationService.MODEL.equals(profile.metadata().model())
                        ? GenerationClientMode.FIXED
                        : GenerationClientMode.TERRA,
                profile);
        return switch (profile.version()) {
            case V1 -> new V1Validation(
                    new com.ceos24.autedi.domain.generation.contract.v1.validation.AiCorrectionResponseValidator()
                            .validate(
                                    new com.ceos24.autedi.domain.generation.contract.v1.AiCorrectionResponseParser()
                                            .parse(raw),
                                    input.v1()));
            case V1_1 -> new V11Validation(
                    new com.ceos24.autedi.domain.generation.contract.v1_1.validation.AiCorrectionResponseValidator()
                            .validate(
                                    new com.ceos24.autedi.domain.generation.contract.v1_1.AiCorrectionResponseParser()
                                            .parse(raw),
                                    input.v11()));
        };
    }
}
