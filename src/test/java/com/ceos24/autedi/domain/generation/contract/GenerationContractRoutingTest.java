package com.ceos24.autedi.domain.generation.contract;

import static org.junit.jupiter.api.Assertions.*;

import com.ceos24.autedi.domain.generation.client.GenerationClientProfiles;
import com.ceos24.autedi.domain.generation.contract.v1.AiArea;
import com.ceos24.autedi.domain.generation.contract.v1.validation.AiCorrectionLevel;
import com.ceos24.autedi.domain.generation.contract.v1_1.prompt.AiCorrectionPromptVariant;
import com.ceos24.autedi.domain.generation.entity.GenerationClientMode;
import com.ceos24.autedi.domain.generation.entity.WorksheetGeneration;
import com.ceos24.autedi.domain.generation.persistence.GenerationResultMetadata;
import com.ceos24.autedi.global.config.GenerationContractConfiguration;
import com.ceos24.autedi.global.exception.CustomException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import tools.jackson.databind.json.JsonMapper;

class GenerationContractRoutingTest {
    private final GenerationContractRouter router = new GenerationContractRouter();

    @Test
    void defaultsAndBindingKeepV1AndExplicitlySelectV11Compact() {
        var context = new ApplicationContextRunner().withUserConfiguration(GenerationContractConfiguration.class);
        context.run(app -> {
            assertNull(app.getStartupFailure());
            var settings = app.getBean(GenerationContractProperties.class);
            assertEquals(GenerationContractVersion.V1, settings.version());
            assertEquals(AiCorrectionPromptVariant.DEFAULT, settings.variant());
        });
        context.withPropertyValues(
                        "app.ai.correction.contract-version=v1.1", "app.ai.correction.prompt-variant=compact")
                .run(app -> {
                    assertNull(app.getStartupFailure());
                    var settings = app.getBean(GenerationContractProperties.class);
                    assertEquals(GenerationContractVersion.V1_1, settings.version());
                    assertEquals(AiCorrectionPromptVariant.COMPACT, settings.variant());
                });
        context.withPropertyValues("app.ai.correction.contract-version=v9")
                .run(app -> assertNotNull(app.getStartupFailure()));
    }

    @ParameterizedTest
    @CsvSource({"v1,DEFAULT", "v1.1,DEFAULT", "v1.1,COMPACT"})
    void routesMatchingPromptSchemaAndMetadata(String version, String variant) {
        var profile = GenerationClientProfiles.profile(
                GenerationClientMode.TERRA,
                GenerationContractVersion.fromSetting(version),
                AiCorrectionPromptVariant.valueOf(variant));
        var input = input("She go home.", AiArea.GRAMMAR_SPELLING, AiCorrectionLevel.STANDARD);
        var prompt = router.compose(profile, input);
        assertEquals(profile, prompt.profile());
        assertTrue(prompt.responseSchema().contains(profile.version().contractId()));
        assertTrue(prompt.instructions().contains("STANDARD"));
        assertTrue(prompt.inputText().contains("She go home."));
        assertEquals(
                profile,
                JsonMapper.builder()
                        .build()
                        .readValue(
                                JsonMapper.builder().build().writeValueAsString(profile), GenerationAiProfile.class));
    }

    @Test
    void compactChangesOnlyPromptWhileInputSchemaAndContractStayTheSame() {
        var input = input("  She go home.  ", AiArea.GRAMMAR_SPELLING, AiCorrectionLevel.STANDARD);
        var normal = router.compose(profile(GenerationContractVersion.V1_1, AiCorrectionPromptVariant.DEFAULT), input);
        var compact = router.compose(profile(GenerationContractVersion.V1_1, AiCorrectionPromptVariant.COMPACT), input);
        assertEquals(normal.inputText(), compact.inputText());
        assertEquals(normal.responseSchema(), compact.responseSchema());
        assertNotEquals(
                normal.profile().metadata().promptVersion(),
                compact.profile().metadata().promptVersion());
        assertTrue(normal.instructions().length() > compact.instructions().length());
        assertThrows(UnsupportedOperationException.class, () -> input.lines().clear());
        assertThrows(UnsupportedOperationException.class, () -> input.settings().clear());
    }

    @Test
    void selectsCorrectParserAndValidatorAndNeverFallsBackToAnotherContract() throws Exception {
        var v1 = profile(GenerationContractVersion.V1, AiCorrectionPromptVariant.DEFAULT);
        var v11 = profile(GenerationContractVersion.V1_1, AiCorrectionPromptVariant.DEFAULT);
        String oldJson = resource("legacy-v1-fields.json"), newJson = resource("standard-grammar-replace.json");
        var oldInput = input("I have dog.", AiArea.GRAMMAR_SPELLING, AiCorrectionLevel.STANDARD);
        var newInput = input("She go to school every day.", AiArea.GRAMMAR_SPELLING, AiCorrectionLevel.STANDARD);
        var old = assertInstanceOf(
                GenerationContractRouter.V1Validation.class, router.parseAndValidate(v1, oldJson, oldInput));
        var current = assertInstanceOf(
                GenerationContractRouter.V11Validation.class, router.parseAndValidate(v11, newJson, newInput));
        assertEquals(1, old.result().corrections().size());
        assertEquals(1, current.result().cards().size());
        assertEquals(5, old.result().summary().getFirst().maximumCount());
        assertEquals(7, current.result().summary().getFirst().maximumCount());
        assertThrows(IllegalArgumentException.class, () -> router.parseAndValidate(v1, newJson, newInput));
        assertThrows(IllegalArgumentException.class, () -> router.parseAndValidate(v11, oldJson, oldInput));
    }

    @Test
    void v11RoutingPreservesMultilineAnchorsAndStructuredEdits() throws Exception {
        var input = new GenerationContractRouter.Input(
                1,
                List.of("I finished my homework.", "I took a shower.", "I went to bed."),
                Map.of(AiArea.SENTENCE_STRUCTURE, AiCorrectionLevel.ADVANCED));
        var result = assertInstanceOf(
                        GenerationContractRouter.V11Validation.class,
                        router.parseAndValidate(
                                profile(GenerationContractVersion.V1_1, AiCorrectionPromptVariant.DEFAULT),
                                resource("advanced-structure-multiline.json"),
                                input))
                .result();
        assertEquals(1, result.cards().size());
        assertEquals(3, result.cards().getFirst().original().anchors().size());
        assertEquals(
                "RESTRUCTURE",
                result.cards().getFirst().original().edit().operation().name());
    }

    @ParameterizedTest
    @EnumSource(GenerationClientMode.class)
    void retriesAndUnversionedLegacyJobsUseTheirOriginalV1Profile(GenerationClientMode mode) {
        var pinned =
                GenerationClientProfiles.profile(mode, GenerationContractVersion.V1, AiCorrectionPromptVariant.DEFAULT);
        var changedConfig = new GenerationContractSelector(new GenerationContractProperties("v1.1", "COMPACT"));
        var job = WorksheetGeneration.builder()
                .clientMode(mode)
                .requestAiProfile(pinned)
                .build();
        assertEquals(pinned, changedConfig.forGeneration(mode, job));
        var legacy = WorksheetGeneration.builder().clientMode(mode).build();
        assertEquals(pinned, changedConfig.forGeneration(mode, legacy));
    }

    @Test
    void retryOfAnOlderJobWithoutClientModeUsesTheResolvedModeAndV1() {
        var selector = new GenerationContractSelector(new GenerationContractProperties("v1.1", "COMPACT"));
        assertEquals(
                GenerationClientProfiles.profile(
                        GenerationClientMode.FIXED, GenerationContractVersion.V1, AiCorrectionPromptVariant.DEFAULT),
                selector.forGeneration(
                        GenerationClientMode.FIXED,
                        WorksheetGeneration.builder().build()));
    }

    @ParameterizedTest
    @EnumSource(AiCorrectionPromptVariant.class)
    void knownV11ProfilesCanStartTerraServiceJobs(AiCorrectionPromptVariant variant) {
        var selector = new GenerationContractSelector(new GenerationContractProperties("v1.1", variant.name()));
        var selected = selector.configuredProfile(GenerationClientMode.TERRA);
        assertEquals(GenerationContractVersion.V1_1, selected.version());
        assertEquals(selected, selector.forGeneration(GenerationClientMode.TERRA, null));
        var changed = new GenerationContractSelector(new GenerationContractProperties("v1", "DEFAULT"));
        assertEquals(
                selected,
                changed.forGeneration(
                        GenerationClientMode.TERRA,
                        WorksheetGeneration.builder()
                                .clientMode(GenerationClientMode.TERRA)
                                .requestAiProfile(selected)
                                .build()));
    }

    @Test
    void unknownModelOrPromptFingerprintIsRejectedInsteadOfReinterpreted() {
        var known = profile(GenerationContractVersion.V1, AiCorrectionPromptVariant.DEFAULT);
        var unsupported = new GenerationAiProfile(
                known.version(),
                known.promptVariant(),
                new GenerationResultMetadata(
                        known.metadata().model(),
                        known.metadata().contractVersion(),
                        "unavailable-prompt",
                        known.metadata().validationVersion()));
        assertThrows(
                IllegalArgumentException.class,
                () -> router.compose(unsupported, input("Text.", AiArea.GRAMMAR_SPELLING, AiCorrectionLevel.STANDARD)));
        var selector = new GenerationContractSelector(new GenerationContractProperties("v1", "DEFAULT"));
        assertThrows(
                CustomException.class,
                () -> selector.forGeneration(
                        GenerationClientMode.TERRA,
                        WorksheetGeneration.builder()
                                .clientMode(GenerationClientMode.TERRA)
                                .requestAiProfile(unsupported)
                                .build()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "v2", "v1.10", " v1", "AUTEDI-AI-CONTRACT-v1"})
    void rejectsUnknownConfigurationVersions(String value) {
        assertThrows(IllegalArgumentException.class, () -> new GenerationContractProperties(value, "DEFAULT"));
    }

    @Test
    void rejectsUnsupportedVariantAndProviderCombinations() {
        assertThrows(IllegalArgumentException.class, () -> new GenerationContractProperties("v1", "COMPACT"));
        assertThrows(IllegalArgumentException.class, () -> new GenerationContractProperties("v1.1", "UNKNOWN"));
        assertThrows(
                IllegalArgumentException.class,
                () -> GenerationClientProfiles.profile(
                        GenerationClientMode.FIXED, GenerationContractVersion.V1_1, AiCorrectionPromptVariant.DEFAULT));
    }

    private static GenerationAiProfile profile(GenerationContractVersion version, AiCorrectionPromptVariant variant) {
        return GenerationClientProfiles.profile(GenerationClientMode.TERRA, version, variant);
    }

    private static GenerationContractRouter.Input input(String line, AiArea area, AiCorrectionLevel level) {
        return new GenerationContractRouter.Input(1, List.of(line, ""), Map.of(area, level));
    }

    private static String resource(String filename) throws Exception {
        try (var input =
                GenerationContractRoutingTest.class.getResourceAsStream("/ai/contract/v1.1/responses/" + filename)) {
            return new String(java.util.Objects.requireNonNull(input).readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
