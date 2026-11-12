package com.ceos24.autedi.domain.generation.contract.v1_1.validation;

import static org.junit.jupiter.api.Assertions.*;

import com.ceos24.autedi.domain.generation.contract.v1_1.AiAction;
import com.ceos24.autedi.domain.generation.contract.v1_1.AiAnchor;
import com.ceos24.autedi.domain.generation.contract.v1_1.AiArea;
import com.ceos24.autedi.domain.generation.contract.v1_1.AiCorrectionItem;
import com.ceos24.autedi.domain.generation.contract.v1_1.AiCorrectionResponse;
import com.ceos24.autedi.domain.generation.contract.v1_1.AiCorrectionResponseParser;
import com.ceos24.autedi.domain.generation.contract.v1_1.AiEdit;
import com.ceos24.autedi.domain.generation.contract.v1_1.AiEditOperation;
import com.ceos24.autedi.domain.generation.contract.v1_1.AiFeedbackType;
import com.ceos24.autedi.domain.generation.contract.v1_1.AiIssueKind;
import com.ceos24.autedi.domain.generation.contract.v1_1.AiRevisionScope;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/** 고정 응답의 실제 파서 → 원문·좌표 → 동작 → 정책·제한 연결. AI·DB·서비스는 실행하지 않는다. */
class AiCorrectionResponseValidatorTest {
    private static final String ROOT = "/ai/contract/v1.1/";
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final List<List<Double>> BOX = List.of(List.of(0.1, 0.2, 0.4, 0.3));
    private final AiCorrectionResponseParser parser = new AiCorrectionResponseParser();
    private final AiCorrectionResponseValidator validator = new AiCorrectionResponseValidator();

    @ParameterizedTest(name = "{0}")
    @MethodSource("readableFixtures")
    void matchesFixtureClassificationLocationsWarningsCountsAndDisplayOrder(String id, JsonNode fixture)
            throws IOException {
        AiCorrectionResponse response =
                parser.parse(resource(fixture.get("response_file").asString()));
        JsonNode original = JSON.valueToTree(response);
        AiValidationResult result = validator.validate(response, context(fixture));
        JsonNode expected = fixture.get("expected");
        assertSame(response, result.originalResponse());
        assertEquals(original, JSON.valueToTree(response));
        assertEquals(response.items().size(), result.candidates().size());
        for (int i = 0; i < response.items().size(); i++) {
            assertEquals(i, result.candidates().get(i).candidateIndex());
            assertSame(response.items().get(i), result.candidates().get(i).original());
        }
        assertIndices(expected.get("cards"), result.cards());
        assertIndices(expected.get("reviews"), result.reviewIssues());
        assertIndices(expected.get("excluded"), result.excludedItems());
        assertEquals(
                integers(expected.get("display_order")),
                result.cards().stream().map(AiValidatedItem::candidateIndex).toList());
        for (String group : List.of("cards", "reviews", "excluded")) {
            for (JsonNode item : expected.get(group)) {
                AiValidatedItem checked =
                        result.candidates().get(item.get("candidate_index").intValue());
                for (JsonNode tag : item.get("issue_tags")) assertHasCode(checked, codeFor(tag.asString()));
                if (!group.equals("cards")) {
                    assertFalse(checked.canAutoPlace());
                    continue;
                }
                assertEquals(
                        item.get("original_text").asString(), checked.location().originalText());
                assertEquals(item.get("requires_review").asBoolean(), checked.requiresReview());
                assertEquals(item.get("auto_place").asBoolean(), checked.canAutoPlace());
                List<Map<String, Integer>> anchors = checked.location().resolvedAnchors().stream()
                        .map(a -> Map.of("line_id", a.lineId(), "start", a.start(), "end", a.end()))
                        .toList();
                assertEquals(item.get("resolved_anchors"), JSON.valueToTree(anchors));
                assertEquals(
                        item.get("valid_target_boxes"),
                        JSON.valueToTree(checked.location().validTargetBoxes()));
            }
        }
        assertEquals(AiArea.values().length, result.summary().size());
        for (JsonNode expectedArea : expected.get("summary")) {
            AiAreaSummary actual =
                    summary(result, AiArea.valueOf(expectedArea.get("area").asString()));
            assertEquals(
                    expectedArea.get("level").isNull()
                            ? null
                            : AiCorrectionLevel.valueOf(
                                    expectedArea.get("level").asString()),
                    actual.level());
            assertEquals(expectedArea.get("maximum_count").intValue(), actual.maximumCount());
            assertEquals(expectedArea.get("validated_count").intValue(), actual.validatedCount());
            assertEquals(expectedArea.get("displayed_count").intValue(), actual.displayedCount());
            assertEquals(expectedArea.get("omitted_count").intValue(), actual.omittedCount());
        }
        assertEquals(
                result.summary().stream().mapToInt(AiAreaSummary::omittedCount).sum(), result.omittedCount());
    }

    static Stream<Arguments> readableFixtures() throws IOException {
        List<Arguments> cases = new ArrayList<>();
        for (JsonNode fixture : JSON.readTree(resource("cases.json")).get("cases")) {
            if (fixture.at("/expected/parse_result").asString().equals("ACCEPT")) {
                cases.add(Arguments.of(fixture.get("id").asString(), fixture));
            }
        }
        return cases.stream();
    }

    @ParameterizedTest
    @EnumSource(AiArea.class)
    void excludesDisabledAreasAndStandardSuggestionsWithoutChangingTheOriginalType(AiArea area) {
        AiCorrectionItem item = replacement(1, "go", "goes", area, AiFeedbackType.SUGGESTION);
        AiValidatedItem disabled =
                check(List.of(item), List.of("go"), Map.of()).candidates().getFirst();
        assertDisposition(disabled, AiItemDisposition.EXCLUDED, AiValidationCode.AREA_NOT_SELECTED);
        AiValidatedItem standard = check(List.of(item), List.of("go"), Map.of(area, AiCorrectionLevel.STANDARD))
                .candidates()
                .getFirst();
        assertDisposition(standard, AiItemDisposition.EXCLUDED, AiValidationCode.STANDARD_SUGGESTION);
        assertEquals(AiFeedbackType.SUGGESTION, standard.original().feedbackType());
        AiValidatedItem advanced = check(List.of(item), List.of("go"), Map.of(area, AiCorrectionLevel.ADVANCED))
                .candidates()
                .getFirst();
        assertEquals(AiItemDisposition.CARD, advanced.disposition());
    }

    @Test
    void appliesLevelsIndependentlyAndPreventsMixedIssueBypass() {
        Map<AiArea, AiCorrectionLevel> selected = Map.of(
                AiArea.GRAMMAR_SPELLING,
                AiCorrectionLevel.STANDARD,
                AiArea.VOCABULARY_EXPRESSION,
                AiCorrectionLevel.ADVANCED);
        AiCorrectionItem grammar = replacement(1, "go", "goes", AiArea.GRAMMAR_SPELLING, AiFeedbackType.SUGGESTION);
        AiCorrectionItem vocabulary =
                replacement(2, "good", "excellent", AiArea.VOCABULARY_EXPRESSION, AiFeedbackType.SUGGESTION);
        AiValidationResult result = check(List.of(grammar, vocabulary), List.of("go", "good"), selected);
        assertEquals(
                List.of(1),
                result.cards().stream().map(AiValidatedItem::candidateIndex).toList());
        AiCorrectionItem mixed = withIssues(vocabulary, List.of(AiIssueKind.VOCABULARY, AiIssueKind.GRAMMAR));
        AiValidatedItem standardBypass = check(List.of(mixed), List.of("go", "good"), selected)
                .candidates()
                .getFirst();
        assertDisposition(standardBypass, AiItemDisposition.EXCLUDED, AiValidationCode.STANDARD_SUGGESTION);
        AiValidatedItem disabledBypass = check(
                        List.of(mixed),
                        List.of("go", "good"),
                        Map.of(AiArea.VOCABULARY_EXPRESSION, AiCorrectionLevel.ADVANCED))
                .candidates()
                .getFirst();
        assertDisposition(disabledBypass, AiItemDisposition.EXCLUDED, AiValidationCode.ISSUE_AREA_NOT_SELECTED);
        AiValidatedItem allowedMixed = check(
                        List.of(mixed),
                        List.of("go", "good"),
                        Map.of(
                                AiArea.GRAMMAR_SPELLING,
                                AiCorrectionLevel.ADVANCED,
                                AiArea.VOCABULARY_EXPRESSION,
                                AiCorrectionLevel.ADVANCED))
                .candidates()
                .getFirst();
        assertEquals(AiItemDisposition.CARD, allowedMixed.disposition());
    }

    @ParameterizedTest
    @EnumSource(AiIssueKind.class)
    void checksIssueKindFamiliesAndRepresentativeArea(AiIssueKind kind) {
        AiCorrectionItem item = withIssues(
                replacement(1, "go", "goes", AiArea.GRAMMAR_SPELLING, AiFeedbackType.CORRECTION), List.of(kind));
        AiValidationResult result = check(List.of(item), List.of("go"), allAreas(AiCorrectionLevel.ADVANCED));
        if (Set.of(AiIssueKind.GRAMMAR, AiIssueKind.SPELLING, AiIssueKind.PUNCTUATION)
                .contains(kind)) {
            assertEquals(AiItemDisposition.CARD, result.candidates().getFirst().disposition());
        } else {
            assertDisposition(
                    result.candidates().getFirst(),
                    AiItemDisposition.REVIEW,
                    Set.of(AiIssueKind.VOCABULARY, AiIssueKind.SENTENCE_STRUCTURE, AiIssueKind.CONTENT)
                                    .contains(kind)
                            ? AiValidationCode.AREA_ISSUE_MISMATCH
                            : AiValidationCode.INVALID_ISSUE_KINDS);
        }
        AiCorrectionItem review = new AiCorrectionItem(
                AiAction.REVIEW,
                null,
                null,
                List.of(kind),
                List.of(),
                null,
                null,
                "원문 확인이 필요합니다.",
                true,
                List.of(),
                null,
                null);
        AiValidatedItem reviewed =
                check(List.of(review), List.of("go"), Map.of()).candidates().getFirst();
        assertEquals(AiItemDisposition.REVIEW, reviewed.disposition());
        assertEquals(
                kind.ordinal() < AiIssueKind.OCR_MISMATCH.ordinal(),
                reviewed.issues().stream().anyMatch(i -> i.code() == AiValidationCode.INVALID_ISSUE_KINDS));
    }

    @ParameterizedTest
    @MethodSource("scopeCases")
    void checksAreaAndOperationScopeMatrix(
            AiArea area, AiRevisionScope scope, AiEditOperation operation, AiValidationCode error) {
        String source =
                switch (scope) {
                    case WORD -> "go";
                    case PHRASE -> "go home";
                    case CLAUSE -> "I go home";
                    case SENTENCE -> "I go home.";
                    case SENTENCE_GROUP -> "I go home. I sleep.";
                };
        AiCorrectionItem base = replacement(1, source, source.replace("go", "went"), area, AiFeedbackType.CORRECTION);
        AiCorrectionItem item = withScope(base, scope, operation);
        AiValidatedItem result = check(List.of(item), List.of(source), Map.of(area, AiCorrectionLevel.ADVANCED))
                .candidates()
                .getFirst();
        if (error == null)
            assertEquals(
                    AiItemDisposition.CARD,
                    result.disposition(),
                    result.issues().toString());
        else assertDisposition(result, AiItemDisposition.REVIEW, error);
    }

    static Stream<Arguments> scopeCases() {
        List<Arguments> cases = new ArrayList<>();
        for (AiArea area : List.of(AiArea.GRAMMAR_SPELLING, AiArea.VOCABULARY_EXPRESSION, AiArea.CONTENT_SUMMARY)) {
            cases.add(Arguments.of(area, AiRevisionScope.WORD, AiEditOperation.REPLACE, null));
            cases.add(Arguments.of(area, AiRevisionScope.PHRASE, AiEditOperation.REPLACE, null));
            cases.add(Arguments.of(
                    area, AiRevisionScope.CLAUSE, AiEditOperation.REPLACE, AiValidationCode.AREA_SCOPE_VIOLATION));
            cases.add(Arguments.of(
                    area,
                    AiRevisionScope.SENTENCE,
                    AiEditOperation.RESTRUCTURE,
                    AiValidationCode.AREA_SCOPE_VIOLATION));
            cases.add(Arguments.of(
                    area,
                    AiRevisionScope.SENTENCE_GROUP,
                    AiEditOperation.RESTRUCTURE,
                    AiValidationCode.AREA_SCOPE_VIOLATION));
            cases.add(Arguments.of(
                    area,
                    AiRevisionScope.PHRASE,
                    AiEditOperation.RESTRUCTURE,
                    AiValidationCode.SCOPE_OPERATION_MISMATCH));
        }
        for (AiRevisionScope scope : List.of(AiRevisionScope.WORD, AiRevisionScope.PHRASE, AiRevisionScope.CLAUSE)) {
            cases.add(Arguments.of(AiArea.SENTENCE_STRUCTURE, scope, AiEditOperation.REPLACE, null));
            cases.add(Arguments.of(
                    AiArea.SENTENCE_STRUCTURE,
                    scope,
                    AiEditOperation.RESTRUCTURE,
                    AiValidationCode.SCOPE_OPERATION_MISMATCH));
        }
        for (AiRevisionScope scope : List.of(AiRevisionScope.SENTENCE, AiRevisionScope.SENTENCE_GROUP)) {
            cases.add(Arguments.of(AiArea.SENTENCE_STRUCTURE, scope, AiEditOperation.RESTRUCTURE, null));
            cases.add(Arguments.of(
                    AiArea.SENTENCE_STRUCTURE,
                    scope,
                    AiEditOperation.REPLACE,
                    AiValidationCode.SCOPE_OPERATION_MISMATCH));
        }
        return cases.stream();
    }

    @Test
    void checksWordScopeAgainstVerifiedSourceWithoutUsingPhysicalLineCountAsScope() {
        AiCorrectionItem phrase = replacement(
                1, "made homework", "did homework", AiArea.VOCABULARY_EXPRESSION, AiFeedbackType.CORRECTION);
        AiValidatedItem mismatch = check(
                        List.of(phrase), List.of("made homework"), allAreas(AiCorrectionLevel.STANDARD))
                .candidates()
                .getFirst();
        assertDisposition(mismatch, AiItemDisposition.REVIEW, AiValidationCode.SCOPE_TEXT_MISMATCH);
        for (String word : List.of("student’s", "well-known", "well\u2011known", "word123")) {
            AiCorrectionItem item =
                    replacement(1, word, "changed", AiArea.VOCABULARY_EXPRESSION, AiFeedbackType.CORRECTION);
            assertEquals(
                    AiItemDisposition.CARD,
                    check(List.of(item), List.of(word), allAreas(AiCorrectionLevel.STANDARD))
                            .candidates()
                            .getFirst()
                            .disposition());
        }
    }

    @Test
    void resetsCountsForEachPageAndKeepsTheGenerationSettingsSnapshot() {
        List<AiCorrectionItem> items = IntStream.rangeClosed(1, 8)
                .mapToObj(line -> replacement(line, "go", "goes", AiArea.GRAMMAR_SPELLING, AiFeedbackType.CORRECTION))
                .toList();
        List<String> lines = IntStream.range(0, 8).mapToObj(i -> "go").toList();
        AiCorrectionResponse response = new AiCorrectionResponse(items);
        for (int page : List.of(1, 2)) {
            AiValidationResult result = validator.validate(
                    response,
                    new AiValidationContext(page, lines, Map.of(AiArea.GRAMMAR_SPELLING, AiCorrectionLevel.STANDARD)));
            assertEquals(7, result.cards().size());
            assertEquals(1, result.excludedItems().size());
            assertTrue(result.candidates().stream()
                    .allMatch(item -> item.location().pageNumber() == page));
        }
    }

    @ParameterizedTest
    @MethodSource("guidanceCases")
    void countsGuidanceWordsAndChecksLanguage(String text, AiValidationCode error) {
        AiCorrectionItem item = guidance(1, "book", text, AiFeedbackType.CORRECTION);
        AiValidatedItem result = check(
                        List.of(item), List.of("book"), Map.of(AiArea.CONTENT_SUMMARY, AiCorrectionLevel.STANDARD))
                .candidates()
                .getFirst();
        if (error == null)
            assertEquals(
                    AiItemDisposition.CARD,
                    result.disposition(),
                    result.issues().toString());
        else assertDisposition(result, AiItemDisposition.REVIEW, error);
    }

    static Stream<Arguments> guidanceCases() {
        return Stream.of(
                Arguments.of("Add one reason you like this book to your paragraph.", null),
                Arguments.of(
                        "Add one clear reason you like this book to your paragraph.",
                        AiValidationCode.GUIDANCE_TOO_LONG),
                Arguments.of("Explain the student's well-known idea using 2 clear examples.", null),
                Arguments.of("Explain the student’s well\u2011known idea using 2 clear examples.", null),
                Arguments.of("a b c d e f g h i 2", null),
                Arguments.of("a b c d e f g h i 2 3", AiValidationCode.GUIDANCE_TOO_LONG),
                Arguments.of("이유를 한 개 추가하세요.", AiValidationCode.GUIDANCE_NOT_ENGLISH),
                Arguments.of("Add 이유 here.", AiValidationCode.GUIDANCE_NOT_ENGLISH),
                Arguments.of("Add a café example.", AiValidationCode.GUIDANCE_NOT_ENGLISH),
                Arguments.of("Add a cafe\u0301 example.", AiValidationCode.GUIDANCE_NOT_ENGLISH),
                Arguments.of("123 456", AiValidationCode.GUIDANCE_NOT_ENGLISH),
                Arguments.of("?!", AiValidationCode.GUIDANCE_NOT_ENGLISH));
    }

    @ParameterizedTest
    @EnumSource(AiCorrectionLevel.class)
    void countsReplaceAndGuidanceTogetherPerAreaAndIgnoresReviewAndInvalidCandidates(AiCorrectionLevel level) {
        List<AiCorrectionItem> items = new ArrayList<>();
        List<String> lines = new ArrayList<>();
        AiCorrectionItem bad = replacement(1, "missing", "changed", AiArea.CONTENT_SUMMARY, AiFeedbackType.CORRECTION);
        items.add(bad);
        lines.add("unrelated");
        for (int i = 0; i < 8; i++) {
            lines.add("word" + i);
            items.add(
                    i % 2 == 0
                            ? replacement(
                                    i + 2,
                                    lines.getLast(),
                                    "changed",
                                    AiArea.CONTENT_SUMMARY,
                                    AiFeedbackType.CORRECTION)
                            : guidance(i + 2, lines.getLast(), "Add a reason.", AiFeedbackType.CORRECTION));
        }
        items.add(new AiCorrectionItem(
                AiAction.REVIEW,
                null,
                null,
                List.of(AiIssueKind.IMAGE_PROBLEM),
                List.of(),
                null,
                null,
                "이미지를 확인하세요.",
                true,
                List.of(),
                null,
                null));
        lines.add("go");
        items.add(replacement(lines.size(), "go", "goes", AiArea.GRAMMAR_SPELLING, AiFeedbackType.CORRECTION));
        AiValidationResult result =
                check(items, lines, Map.of(AiArea.CONTENT_SUMMARY, level, AiArea.GRAMMAR_SPELLING, level));
        assertEquals(8, result.cards().size());
        assertEquals(2, result.reviewIssues().size());
        assertEquals(
                List.of(8),
                result.excludedItems().stream()
                        .map(AiValidatedItem::candidateIndex)
                        .toList());
        assertEquals(
                new AiAreaSummary(AiArea.CONTENT_SUMMARY, level, 7, 7, 7, 2), summary(result, AiArea.CONTENT_SUMMARY));
        assertEquals(1, summary(result, AiArea.GRAMMAR_SPELLING).validatedCount());
        assertEquals(2, result.omittedCount());
    }

    @Test
    void selectsCorrectionsBeforeSuggestionsButSortsDisplayedCardsBySource() {
        List<AiCorrectionItem> items = new ArrayList<>();
        List<String> lines =
                IntStream.rangeClosed(1, 9).mapToObj(i -> "word" + i).toList();
        items.add(replacement(1, lines.getFirst(), "better", AiArea.GRAMMAR_SPELLING, AiFeedbackType.SUGGESTION));
        for (int line = 9; line >= 2; line--) {
            items.add(replacement(
                    line, lines.get(line - 1), "changed", AiArea.GRAMMAR_SPELLING, AiFeedbackType.CORRECTION));
        }
        AiValidationResult result = check(items, lines, Map.of(AiArea.GRAMMAR_SPELLING, AiCorrectionLevel.ADVANCED));
        assertEquals(
                List.of(7, 6, 5, 4, 3, 2, 1),
                result.cards().stream().map(AiValidatedItem::candidateIndex).toList());
        assertEquals(
                List.of(0, 8),
                result.excludedItems().stream()
                        .map(AiValidatedItem::candidateIndex)
                        .toList());
        assertEquals(
                IntStream.range(0, 9).boxed().toList(),
                result.candidates().stream()
                        .map(AiValidatedItem::candidateIndex)
                        .toList());
    }

    @Test
    void fillsRemainingPlacesWithSuggestionsInModelOrder() {
        List<String> lines =
                IntStream.rangeClosed(1, 9).mapToObj(i -> "word" + i).toList();
        List<AiCorrectionItem> items = IntStream.range(0, 9)
                .mapToObj(i -> replacement(
                        i + 1,
                        lines.get(i),
                        "changed",
                        AiArea.GRAMMAR_SPELLING,
                        i == 4 || i == 8 ? AiFeedbackType.CORRECTION : AiFeedbackType.SUGGESTION))
                .toList();
        AiValidationResult result = check(items, lines, Map.of(AiArea.GRAMMAR_SPELLING, AiCorrectionLevel.ADVANCED));
        assertEquals(
                List.of(0, 1, 2, 3, 4, 5, 8),
                result.cards().stream().map(AiValidatedItem::candidateIndex).toList());
        assertEquals(
                List.of(6, 7),
                result.excludedItems().stream()
                        .map(AiValidatedItem::candidateIndex)
                        .toList());
    }

    @Test
    void detectsMultilinePartialAndExactOverlapsWithoutDroppingCards() {
        AiCorrectionItem wide = new AiCorrectionItem(
                AiAction.REPLACE,
                AiFeedbackType.CORRECTION,
                AiArea.SENTENCE_STRUCTURE,
                List.of(AiIssueKind.SENTENCE_STRUCTURE),
                List.of(new AiAnchor(1, "alpha beta", 1), new AiAnchor(2, "gamma", 1)),
                "Rewritten sentence.",
                null,
                "문장 구조를 수정합니다.",
                false,
                BOX,
                AiRevisionScope.SENTENCE,
                new AiEdit(AiEditOperation.RESTRUCTURE, "Rewritten sentence.", null));
        AiCorrectionItem overlap = replacement(2, "gamma", "delta", AiArea.GRAMMAR_SPELLING, AiFeedbackType.CORRECTION);
        AiCorrectionItem adjacent = replacement(2, "tail", "end", AiArea.GRAMMAR_SPELLING, AiFeedbackType.CORRECTION);
        AiValidationResult result = check(
                List.of(wide, overlap, overlap, adjacent),
                List.of("alpha beta", "gamma tail"),
                allAreas(AiCorrectionLevel.STANDARD));
        assertEquals(4, result.cards().size());
        for (int index : List.of(0, 1, 2)) {
            AiValidatedItem item = result.candidates().get(index);
            assertHasCode(item, AiValidationCode.OVERLAPPING_ANCHORS);
            assertTrue(item.requiresReview());
            assertFalse(item.canAutoPlace());
        }
        assertTrue(result.candidates().get(3).canAutoPlace());
    }

    @Test
    void doesNotWarnAboutReviewExcludedOrMerelyAdjacentRanges() {
        List<AiCorrectionItem> items = new ArrayList<>();
        List<String> lines = IntStream.range(0, 7).mapToObj(i -> "go").toList();
        for (int i = 0; i < 7; i++)
            items.add(replacement(i + 1, "go", "goes", AiArea.GRAMMAR_SPELLING, AiFeedbackType.CORRECTION));
        items.add(replacement(1, "go", "went", AiArea.GRAMMAR_SPELLING, AiFeedbackType.CORRECTION));
        AiValidationResult limited = check(items, lines, Map.of(AiArea.GRAMMAR_SPELLING, AiCorrectionLevel.STANDARD));
        assertEquals(7, limited.cards().size());
        assertTrue(limited.cards().stream().allMatch(AiValidatedItem::canAutoPlace));

        AiCorrectionItem first = replacement(1, "go", "goes", AiArea.GRAMMAR_SPELLING, AiFeedbackType.CORRECTION);
        AiCorrectionItem touching = replacement(1, "to", "toward", AiArea.GRAMMAR_SPELLING, AiFeedbackType.CORRECTION);
        AiValidationResult adjacent = check(
                List.of(first, touching), List.of("goto"), Map.of(AiArea.GRAMMAR_SPELLING, AiCorrectionLevel.STANDARD));
        assertTrue(adjacent.cards().stream().allMatch(AiValidatedItem::canAutoPlace));
        AiCorrectionItem invalidScope = withScope(first, AiRevisionScope.SENTENCE, AiEditOperation.REPLACE);
        AiCorrectionItem disabled =
                replacement(1, "go", "went", AiArea.VOCABULARY_EXPRESSION, AiFeedbackType.CORRECTION);
        AiValidationResult separated = check(
                List.of(first, invalidScope, disabled),
                List.of("go"),
                Map.of(AiArea.GRAMMAR_SPELLING, AiCorrectionLevel.STANDARD));
        assertTrue(separated.cards().getFirst().canAutoPlace());
    }

    @Test
    void retainsLocationWarningsAndAllEarlierEditDiagnostics() throws IOException {
        for (String name : List.of(
                "uncertain-location-card",
                "partial-invalid-boxes",
                "anchor-not-found-review",
                "edit-replacement-mismatch-review")) {
            JsonNode fixture = fixtureCase(name);
            AiValidationResult result = validator.validate(
                    parser.parse(resource(fixture.get("response_file").asString())), context(fixture));
            AiValidatedItem item = result.candidates().getFirst();
            assertTrue(item.issues().containsAll(item.editValidation().issues()));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"area", "feedback_type", "replacement", "revision_scope", "edit"})
    void keepsNullableButInvalidActionFieldsInReviewInsteadOfThrowing(String field) throws IOException {
        JsonNode fixture = fixtureCase("standard-grammar-replace");
        ObjectNode root =
                (ObjectNode) JSON.readTree(resource(fixture.get("response_file").asString()));
        ((ObjectNode) root.at("/items/0")).set(field, JSON.readTree("null"));
        ((ObjectNode) root.at("/items/0")).put("feedback_type", field.equals("feedback_type") ? null : "SUGGESTION");
        AiValidationResult result = validator.validate(
                parser.parse(root.toString()),
                new AiValidationContext(context(fixture).location(), allAreas(AiCorrectionLevel.ADVANCED)));
        assertDisposition(
                result.candidates().getFirst(), AiItemDisposition.REVIEW, AiValidationCode.INVALID_ACTION_FIELDS);
    }

    @Test
    void supportsEmptyResponsesAndImmutableContextAndResults() {
        Map<AiArea, AiCorrectionLevel> settings = new EnumMap<>(AiArea.class);
        settings.put(AiArea.GRAMMAR_SPELLING, AiCorrectionLevel.STANDARD);
        List<String> lines = new ArrayList<>(List.of("go"));
        AiValidationContext context = new AiValidationContext(3, lines, settings);
        settings.clear();
        lines.clear();
        assertEquals(1, context.selectedAreas().size());
        assertEquals(List.of("go"), context.location().transcriptionLines());
        AiValidationResult result = validator.validate(new AiCorrectionResponse(List.of()), context);
        assertTrue(result.cards().isEmpty());
        assertTrue(result.reviewIssues().isEmpty());
        assertEquals(0, result.omittedCount());
        assertEquals(7, summary(result, AiArea.GRAMMAR_SPELLING).maximumCount());
        assertEquals(0, summary(result, AiArea.CONTENT_SUMMARY).maximumCount());
        assertThrows(UnsupportedOperationException.class, () -> context.selectedAreas()
                .clear());
        assertThrows(
                UnsupportedOperationException.class, () -> result.candidates().clear());
        assertThrows(UnsupportedOperationException.class, () -> result.summary().clear());
        assertThrows(UnsupportedOperationException.class, () -> result.cards().clear());
        AiValidatedItem excluded = check(
                        List.of(replacement(1, "go", "goes", AiArea.GRAMMAR_SPELLING, AiFeedbackType.CORRECTION)),
                        List.of("go"),
                        Map.of())
                .candidates()
                .getFirst();
        assertThrows(UnsupportedOperationException.class, () -> excluded.policyIssues()
                .clear());
        assertThrows(
                UnsupportedOperationException.class, () -> excluded.issues().clear());
        assertThrows(NullPointerException.class, () -> validator.validate(null, context));
        assertThrows(NullPointerException.class, () -> validator.validate(new AiCorrectionResponse(List.of()), null));
    }

    private AiValidationResult check(
            List<AiCorrectionItem> items, List<String> lines, Map<AiArea, AiCorrectionLevel> selected) {
        return validator.validate(new AiCorrectionResponse(items), new AiValidationContext(1, lines, selected));
    }

    private static AiCorrectionItem replacement(
            int line, String source, String replacement, AiArea area, AiFeedbackType type) {
        return new AiCorrectionItem(
                AiAction.REPLACE,
                type,
                area,
                List.of(kindFor(area)),
                List.of(new AiAnchor(line, source, 1)),
                replacement,
                null,
                "원문을 해당 영역 기준으로 수정합니다.",
                false,
                BOX,
                AiRevisionScope.WORD,
                new AiEdit(AiEditOperation.REPLACE, replacement, null));
    }

    private static AiCorrectionItem guidance(int line, String source, String text, AiFeedbackType type) {
        return new AiCorrectionItem(
                AiAction.GUIDANCE,
                type,
                AiArea.CONTENT_SUMMARY,
                List.of(AiIssueKind.CONTENT),
                List.of(new AiAnchor(line, source, 1)),
                null,
                text,
                "필요한 설명을 보충하도록 안내합니다.",
                false,
                BOX,
                null,
                null);
    }

    private static AiIssueKind kindFor(AiArea area) {
        return switch (area) {
            case GRAMMAR_SPELLING -> AiIssueKind.GRAMMAR;
            case VOCABULARY_EXPRESSION -> AiIssueKind.VOCABULARY;
            case SENTENCE_STRUCTURE -> AiIssueKind.SENTENCE_STRUCTURE;
            case CONTENT_SUMMARY -> AiIssueKind.CONTENT;
        };
    }

    private static AiCorrectionItem withIssues(AiCorrectionItem i, List<AiIssueKind> kinds) {
        return new AiCorrectionItem(
                i.action(),
                i.feedbackType(),
                i.area(),
                kinds,
                i.anchors(),
                i.replacement(),
                i.guidance(),
                i.reason(),
                i.locationUncertain(),
                i.targetBoxes(),
                i.revisionScope(),
                i.edit());
    }

    private static AiCorrectionItem withScope(AiCorrectionItem i, AiRevisionScope scope, AiEditOperation operation) {
        return new AiCorrectionItem(
                i.action(),
                i.feedbackType(),
                i.area(),
                i.issueKinds(),
                i.anchors(),
                i.replacement(),
                i.guidance(),
                i.reason(),
                i.locationUncertain(),
                i.targetBoxes(),
                scope,
                new AiEdit(operation, i.replacement(), null));
    }

    private static Map<AiArea, AiCorrectionLevel> allAreas(AiCorrectionLevel level) {
        Map<AiArea, AiCorrectionLevel> areas = new EnumMap<>(AiArea.class);
        for (AiArea area : AiArea.values()) areas.put(area, level);
        return areas;
    }

    private static AiValidationContext context(JsonNode fixture) {
        Map<AiArea, AiCorrectionLevel> areas = new EnumMap<>(AiArea.class);
        fixture.at("/context/selected_areas")
                .forEach(area -> areas.put(
                        AiArea.valueOf(area.get("area").asString()),
                        AiCorrectionLevel.valueOf(area.get("level").asString())));
        List<String> lines = new ArrayList<>();
        fixture.at("/context/transcription_lines")
                .forEach(line -> lines.add(line.get("text").asString()));
        return new AiValidationContext(fixture.at("/context/page_number").intValue(), lines, areas);
    }

    private static void assertIndices(JsonNode expected, List<AiValidatedItem> actual) {
        Set<Integer> indices = new java.util.HashSet<>();
        expected.forEach(item -> indices.add(item.get("candidate_index").intValue()));
        assertEquals(
                indices, actual.stream().map(AiValidatedItem::candidateIndex).collect(Collectors.toSet()));
    }

    private static List<Integer> integers(JsonNode array) {
        List<Integer> numbers = new ArrayList<>();
        array.forEach(n -> numbers.add(n.intValue()));
        return numbers;
    }

    private static AiAreaSummary summary(AiValidationResult result, AiArea area) {
        return result.summary().stream()
                .filter(s -> s.area() == area)
                .findFirst()
                .orElseThrow();
    }

    private static void assertDisposition(AiValidatedItem item, AiItemDisposition disposition, AiValidationCode code) {
        assertEquals(disposition, item.disposition(), item.issues().toString());
        assertFalse(item.canAutoPlace());
        assertHasCode(item, code);
    }

    private static void assertHasCode(AiValidatedItem item, AiValidationCode code) {
        assertTrue(
                item.issues().stream().anyMatch(issue -> issue.code() == code),
                item.issues().toString());
    }

    private static AiValidationCode codeFor(String tag) {
        return switch (tag) {
            case "location_uncertain" -> AiValidationCode.MODEL_LOCATION_UNCERTAIN;
            default -> AiValidationCode.valueOf(tag.toUpperCase(java.util.Locale.ROOT));
        };
    }

    private static JsonNode fixtureCase(String id) throws IOException {
        for (JsonNode fixture : JSON.readTree(resource("cases.json")).get("cases")) {
            if (fixture.get("id").asString().equals(id)) return fixture;
        }
        throw new IllegalArgumentException(id);
    }

    private static String resource(String relative) throws IOException {
        try (var input = AiCorrectionResponseValidatorTest.class.getResourceAsStream(ROOT + relative)) {
            assertNotNull(input, relative);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
