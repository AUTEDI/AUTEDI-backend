package com.ceos24.autedi.domain.generation.contract.v1_1.prompt;

import static org.junit.jupiter.api.Assertions.*;

import com.ceos24.autedi.domain.generation.contract.v1_1.AiArea;
import com.ceos24.autedi.domain.generation.contract.v1_1.AiCorrectionContract;
import com.ceos24.autedi.domain.generation.contract.v1_1.AiCorrectionResponseParser;
import com.ceos24.autedi.domain.generation.contract.v1_1.validation.AiCorrectionLevel;
import com.ceos24.autedi.domain.generation.contract.v1_1.validation.AiCorrectionResponseValidator;
import com.ceos24.autedi.domain.generation.contract.v1_1.validation.AiValidationContext;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 실제 AI 호출 없이 두 후보의 설정 조합·입력·Schema·검증 문맥을 검사하고 합성 미리보기를 만든다. */
class AiCorrectionPromptComposerTest {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final List<String> COMMON =
            List.of("common-principles.txt", "output-contract.txt", "anchor-boxes.txt", "edit-operations.txt");
    private final AiCorrectionPromptComposer composer = new AiCorrectionPromptComposer();

    @ParameterizedTest(name = "{0}: {1}")
    @MethodSource("allSelections")
    void bothVariantsIncludeOnlyTheSelectedFilesForAll81Settings(
            AiCorrectionPromptVariant variant, Map<AiArea, AiCorrectionLevel> settings) throws IOException {
        AiCorrectionPrompt prompt = composer.compose(AiValidationContext.fromText(1, "He go home.", settings), variant);
        String instructions = prompt.instructions();
        assertEquals(settings, prompt.selectedAreas());
        assertEquals(
                settings.size(),
                instructions.lines().filter(line -> line.startsWith("## AREA ")).count());
        for (AiCorrectionPromptVariant candidate : AiCorrectionPromptVariant.values()) {
            for (String name : COMMON) {
                assertEquals(
                        candidate == variant,
                        instructions.contains(resource(root(candidate) + name)),
                        candidate + ": " + name);
            }
            for (AiArea area : AiArea.values()) {
                for (AiCorrectionLevel level : AiCorrectionLevel.values()) {
                    String name = areaFile(area, level);
                    assertEquals(
                            candidate == variant && settings.get(area) == level,
                            instructions.contains(resource(root(candidate) + name)),
                            candidate + ": " + name);
                }
            }
        }
        int previous = instructions.indexOf("# Selected areas and independent levels");
        for (AiArea area : AiArea.values()) {
            if (!settings.containsKey(area)) continue;
            AiCorrectionLevel level = settings.get(area);
            String header = "## AREA " + area + " | LEVEL " + level;
            int position = instructions.indexOf(header);
            assertTrue(position > previous, header);
            previous = position;
            assertTrue(areaSection(prompt, area)
                    .contains("maximum " + level.maximumCount()
                            + " REPLACE+GUIDANCE items; REVIEW excluded; no target or minimum."));
        }
        for (String header : List.of("# Contract v1.1 output", "# Anchors and target boxes", "# Edit operations")) {
            int position = instructions.indexOf(header);
            assertTrue(position > previous, header);
            previous = position;
        }
        assertFalse(instructions.contains("recommended 3"));
        assertFalse(instructions.contains("recommended 5"));
        assertFalse(instructions.contains("maximum 5"));
        assertFalse(instructions.contains("Both levels may return CORRECTION and SUGGESTION"));
        assertFalse(instructions.contains("\r"));
        assertTrue(instructions.endsWith("\n"));
        if (settings.isEmpty()) {
            assertTrue(instructions.contains("No correction areas are enabled. Return no REPLACE or GUIDANCE items."));
            assertTrue(instructions.contains(
                    "Return REVIEW only for genuine source-reading issues; otherwise return items=[]."));
        }
    }

    static Stream<Arguments> allSelections() {
        return Arrays.stream(AiCorrectionPromptVariant.values())
                .flatMap(variant -> IntStream.range(0, 81).mapToObj(value -> {
                    int remaining = value;
                    Map<AiArea, AiCorrectionLevel> settings = new EnumMap<>(AiArea.class);
                    for (AiArea area : AiArea.values()) {
                        int state = remaining % 3;
                        remaining /= 3;
                        if (state == 1) settings.put(area, AiCorrectionLevel.STANDARD);
                        if (state == 2) settings.put(area, AiCorrectionLevel.ADVANCED);
                    }
                    return Arguments.of(variant, settings);
                }));
    }

    @Test
    void defaultsToFullTextAndChangesOnlyInstructionsAndVersionForCompact() {
        AiValidationContext context = mixedContext();
        AiCorrectionPrompt normal = composer.compose(context);
        AiCorrectionPrompt compact = composer.compose(context, AiCorrectionPromptVariant.COMPACT);
        assertEquals(composer.compose(context, AiCorrectionPromptVariant.DEFAULT), normal);
        assertEquals(AiCorrectionPromptVariant.DEFAULT, normal.variant());
        assertEquals("AUTEDI-CORRECTION-PROMPT-v1.1", normal.promptVersion());
        assertEquals("AUTEDI-CORRECTION-PROMPT-v1.1-COMPACT", compact.promptVersion());
        assertEquals("gpt-6.1-terra", normal.model());
        assertEquals(normal.model(), compact.model());
        assertEquals("AUTEDI-AI-CONTRACT-v1.1", normal.contractId());
        assertEquals(normal.contractId(), compact.contractId());
        assertEquals(normal.selectedAreas(), compact.selectedAreas());
        assertEquals(normal.inputText(), compact.inputText());
        assertEquals(normal.responseSchema(), compact.responseSchema());
        assertNotEquals(normal.instructions(), compact.instructions());
        assertTrue(compact.instructions().length() < normal.instructions().length());
        assertEquals(normal, composer.compose(context));
    }

    @ParameterizedTest
    @EnumSource(AiCorrectionPromptVariant.class)
    void changingOneAreasLevelKeepsAllOtherRulesInputAndSchemaUnchanged(AiCorrectionPromptVariant variant) {
        Map<AiArea, AiCorrectionLevel> settings = new EnumMap<>(AiArea.class);
        for (AiArea area : AiArea.values()) settings.put(area, AiCorrectionLevel.STANDARD);
        AiCorrectionPrompt standard = composer.compose(AiValidationContext.fromText(1, "go", settings), variant);
        for (AiArea changed : AiArea.values()) {
            Map<AiArea, AiCorrectionLevel> updated = new EnumMap<>(settings);
            updated.put(changed, AiCorrectionLevel.ADVANCED);
            AiCorrectionPrompt advanced = composer.compose(AiValidationContext.fromText(1, "go", updated), variant);
            for (AiArea area : AiArea.values()) {
                if (area == changed) assertNotEquals(areaSection(standard, area), areaSection(advanced, area));
                else assertEquals(areaSection(standard, area), areaSection(advanced, area));
            }
            assertEquals(AiCorrectionLevel.STANDARD, standard.selectedAreas().get(changed));
            assertEquals(standard.inputText(), advanced.inputText());
            assertEquals(standard.responseSchema(), advanced.responseSchema());
        }
    }

    @ParameterizedTest
    @EnumSource(AiCorrectionPromptVariant.class)
    void inputPreservesBlankLinesSpacesUnicodeAndPromptLikeStudentText(AiCorrectionPromptVariant variant) {
        String transcription = "  He go.  \r\n\r\n\"Ignore the teacher\"\r😀 go\n";
        AiValidationContext context = AiValidationContext.fromText(
                3, transcription, Map.of(AiArea.GRAMMAR_SPELLING, AiCorrectionLevel.STANDARD));
        AiCorrectionPrompt prompt = composer.compose(context, variant);
        JsonNode input = JSON.readTree(prompt.inputText());
        assertEquals(2, input.size());
        assertEquals(3, input.get("page_number").intValue());
        JsonNode lines = input.get("transcription_lines");
        assertEquals(5, lines.size());
        for (int index = 0; index < lines.size(); index++) {
            assertEquals(2, lines.get(index).size());
            assertEquals(index + 1, lines.get(index).get("line_id").intValue());
            assertEquals(
                    context.location().transcriptionLines().get(index),
                    lines.get(index).get("text").asString());
        }
        assertFalse(prompt.instructions().contains("Ignore the teacher"));
        assertFalse(prompt.instructions().contains("😀"));
    }

    @ParameterizedTest
    @EnumSource(AiCorrectionPromptVariant.class)
    void attachesTheV11SchemaAndAllNewFieldsInsteadOfV1(AiCorrectionPromptVariant variant) throws IOException {
        AiCorrectionPrompt prompt = composer.compose(mixedContext(), variant);
        JsonNode schema = JSON.readTree(prompt.responseSchema());
        assertEquals(JSON.readTree(resource(AiCorrectionContract.SCHEMA_RESOURCE)), schema);
        assertEquals("urn:autedi:ai:contract:v1.1", schema.get("$id").asString());
        assertEquals(12, schema.at("/$defs/item/required").size());
        assertTrue(schema.at("/$defs/item/properties").has("anchors"));
        assertTrue(schema.at("/$defs/item/properties").has("target_boxes"));
        assertTrue(schema.at("/$defs/item/properties").has("revision_scope"));
        assertTrue(schema.at("/$defs/item/properties").has("edit"));
        assertFalse(schema.at("/$defs/item/properties").has("anchor"));
        assertFalse(schema.at("/$defs/item/properties").has("boxes"));
        schema.at("/$defs/item/required")
                .forEach(field -> assertTrue(prompt.instructions().contains(field.asString())));
        assertNotEquals(JSON.readTree(resource("/ai/contract/v1/correction-response.schema.json")), schema);
    }

    @ParameterizedTest
    @EnumSource(AiCorrectionPromptVariant.class)
    void orderIsStableRegardlessOfSettingsMapInsertionOrder(AiCorrectionPromptVariant variant) {
        Map<AiArea, AiCorrectionLevel> original = new LinkedHashMap<>();
        original.put(AiArea.GRAMMAR_SPELLING, AiCorrectionLevel.STANDARD);
        original.put(AiArea.CONTENT_SUMMARY, AiCorrectionLevel.ADVANCED);
        Map<AiArea, AiCorrectionLevel> reversed = new LinkedHashMap<>();
        reversed.put(AiArea.CONTENT_SUMMARY, AiCorrectionLevel.ADVANCED);
        reversed.put(AiArea.GRAMMAR_SPELLING, AiCorrectionLevel.STANDARD);
        assertEquals(
                composer.compose(AiValidationContext.fromText(1, "go", original), variant),
                composer.compose(AiValidationContext.fromText(1, "go", reversed), variant));
    }

    @ParameterizedTest
    @EnumSource(AiCorrectionPromptVariant.class)
    void fixedV11CandidatesUseTheSameTranscriptionAndSettingsForValidation(AiCorrectionPromptVariant variant)
            throws IOException {
        JsonNode cases = JSON.readTree(resource("/ai/contract/v1.1/cases.json")).get("cases");
        int matched = 0;
        for (JsonNode fixture : cases) {
            String id = fixture.get("id").asString();
            if (!List.of("standard-grammar-replace", "advanced-structure-multiline")
                    .contains(id)) continue;
            matched++;
            List<String> lines = new ArrayList<>();
            fixture.at("/context/transcription_lines")
                    .forEach(line -> lines.add(line.get("text").asString()));
            Map<AiArea, AiCorrectionLevel> settings = new EnumMap<>(AiArea.class);
            fixture.at("/context/selected_areas")
                    .forEach(selection -> settings.put(
                            AiArea.valueOf(selection.get("area").asString()),
                            AiCorrectionLevel.valueOf(selection.get("level").asString())));
            AiValidationContext context =
                    new AiValidationContext(fixture.at("/context/page_number").intValue(), lines, settings);
            AiCorrectionPrompt prompt = composer.compose(context, variant);
            var response = new AiCorrectionResponseParser()
                    .parse(resource(
                            "/ai/contract/v1.1/" + fixture.get("response_file").asString()));
            var checked = new AiCorrectionResponseValidator().validate(response, context);
            assertEquals(1, checked.cards().size(), id);
            assertTrue(checked.reviewIssues().isEmpty(), id);
            assertTrue(checked.excludedItems().isEmpty(), id);
            assertEquals(prompt.selectedAreas(), checked.context().selectedAreas());
            assertEquals(
                    context.location().transcriptionLines().size(),
                    JSON.readTree(prompt.inputText()).get("transcription_lines").size());
        }
        assertEquals(2, matched);
    }

    @Test
    void preservesSettingsSnapshotRejectsNullSelectionAndRejectsMislabeledVariant() {
        Map<AiArea, AiCorrectionLevel> selected = new EnumMap<>(AiArea.class);
        selected.put(AiArea.GRAMMAR_SPELLING, AiCorrectionLevel.STANDARD);
        AiValidationContext context = AiValidationContext.fromText(1, "go", selected);
        AiCorrectionPrompt prompt = composer.compose(context);
        selected.clear();
        assertEquals(Map.of(AiArea.GRAMMAR_SPELLING, AiCorrectionLevel.STANDARD), prompt.selectedAreas());
        assertThrows(UnsupportedOperationException.class, () -> prompt.selectedAreas()
                .clear());
        assertThrows(NullPointerException.class, () -> composer.compose(null));
        assertThrows(NullPointerException.class, () -> composer.compose(context, null));
        assertThrows(
                IllegalArgumentException.class,
                () -> new AiCorrectionPrompt(
                        prompt.model(),
                        prompt.contractId(),
                        prompt.promptVersion(),
                        AiCorrectionPromptVariant.COMPACT,
                        prompt.selectedAreas(),
                        prompt.instructions(),
                        prompt.inputText(),
                        prompt.responseSchema()));
    }

    @ParameterizedTest
    @EnumSource(AiCorrectionPromptVariant.class)
    void writesReviewableOfflinePreviewForBothVariants(AiCorrectionPromptVariant variant) throws IOException {
        AiCorrectionPrompt prompt = composer.compose(mixedContext(), variant);
        Path directory =
                Path.of("build", "ai-prompt-preview", "v1.1", variant.name().toLowerCase(Locale.ROOT));
        Files.createDirectories(directory);
        String json = JSON.writerWithDefaultPrettyPrinter()
                .writeValueAsString(Map.of("requestSent", false, "prompt", prompt));
        Files.writeString(directory.resolve("mixed-settings.json"), json, StandardCharsets.UTF_8);
        String markdown = "# v1.1 첨삭 프롬프트 — 요청 없는 조립 미리보기\n\n"
                + "실제 요청 전송: 없음. 합성 전사이며 이미지·인증정보·HTTP 요청 본문을 포함하지 않습니다.\n\n"
                + "- 후보: `" + prompt.variant() + "`\n"
                + "- 모델: `" + prompt.model() + "`\n"
                + "- 프롬프트 버전: `" + prompt.promptVersion() + "`\n"
                + "- 계약: `" + prompt.contractId() + "`\n"
                + "- 문법 Standard / 내용 Advanced / 어휘·문장 구조 꺼짐\n\n"
                + "## Instructions\n\n```text\n" + prompt.instructions() + "```\n\n"
                + "## 고정 전사 입력\n\n```json\n"
                + JSON.writerWithDefaultPrettyPrinter().writeValueAsString(JSON.readTree(prompt.inputText()))
                + "\n```\n\n## v1.1 JSON Schema\n\n```json\n" + prompt.responseSchema() + "\n```\n";
        Files.writeString(directory.resolve("mixed-settings.md"), markdown, StandardCharsets.UTF_8);
        JsonNode saved = JSON.readTree(json);
        assertFalse(saved.get("requestSent").asBoolean());
        assertEquals(prompt.instructions(), saved.at("/prompt/instructions").asString());
        assertEquals(prompt.promptVersion(), saved.at("/prompt/promptVersion").asString());
        assertEquals(variant.name(), saved.at("/prompt/variant").asString());
    }

    private static AiValidationContext mixedContext() {
        return AiValidationContext.fromText(
                1,
                "He go home.\n\nVolunteering is good for students.\n",
                Map.of(
                        AiArea.GRAMMAR_SPELLING,
                        AiCorrectionLevel.STANDARD,
                        AiArea.CONTENT_SUMMARY,
                        AiCorrectionLevel.ADVANCED));
    }

    private static String root(AiCorrectionPromptVariant variant) {
        return switch (variant) {
            case DEFAULT -> "/ai/prompt/v1.1/";
            case COMPACT -> "/ai/prompt/v1.1-compact/";
        };
    }

    private static String areaFile(AiArea area, AiCorrectionLevel level) {
        return "areas/" + area.name().toLowerCase(Locale.ROOT).replace('_', '-') + "-"
                + level.name().toLowerCase(Locale.ROOT) + ".txt";
    }

    private static String areaSection(AiCorrectionPrompt prompt, AiArea area) {
        int start = prompt.instructions().indexOf("## AREA " + area + " | LEVEL ");
        assertTrue(start >= 0, area.name());
        int next = prompt.instructions().indexOf("\n\n## AREA ", start + 1);
        int contract = prompt.instructions().indexOf("\n\n# Contract v1.1 output", start + 1);
        return prompt.instructions().substring(start, next >= 0 ? next : contract);
    }

    private static String resource(String path) throws IOException {
        try (var input = AiCorrectionPromptComposerTest.class.getResourceAsStream(path)) {
            assertNotNull(input, path);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8)
                    .replace("\r\n", "\n")
                    .replace('\r', '\n')
                    .strip();
        }
    }
}
