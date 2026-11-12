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
import com.ceos24.autedi.domain.generation.contract.v1_1.AiEditPosition;
import com.ceos24.autedi.domain.generation.contract.v1_1.AiFeedbackType;
import com.ceos24.autedi.domain.generation.contract.v1_1.AiIssueKind;
import com.ceos24.autedi.domain.generation.contract.v1_1.AiRevisionScope;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/** 고정 후보의 파서 → 원문·좌표 → 수정 동작 연결만 검사한다. AI·DB·레벨 정책은 실행하지 않는다. */
class AiEditValidatorTest {
    private static final String ROOT = "/ai/contract/v1.1/";
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final List<List<Double>> BOX = List.of(List.of(0.1, 0.2, 0.4, 0.3));
    private final AiCorrectionResponseParser parser = new AiCorrectionResponseParser();
    private final AiLocationValidator locations = new AiLocationValidator();
    private final AiEditValidator validator = new AiEditValidator();

    @ParameterizedTest(name = "{0}")
    @MethodSource("readableFixtures")
    void validatesFixtureEditsAndPreservesAllOriginalCandidates(String id, JsonNode fixture) throws IOException {
        AiCorrectionResponse response =
                parser.parse(resource(fixture.get("response_file").asString()));
        JsonNode before = JSON.valueToTree(response);
        List<AiLocationValidationResult> located = locations.validate(response, context(fixture));
        List<AiEditValidationResult> results = validator.validate(located);
        assertEquals(located.size(), results.size(), id);
        assertEquals(before, JSON.valueToTree(response), id);
        for (int index = 0; index < results.size(); index++) {
            assertSame(located.get(index), results.get(index).location(), id);
            assertEquals(index, results.get(index).location().candidateIndex(), id);
            assertTrue(
                    results.get(index).issues().containsAll(located.get(index).issues()), id);
        }
        for (JsonNode expected : fixture.at("/expected/cards")) {
            AiEditValidationResult result =
                    results.get(expected.get("candidate_index").intValue());
            assertEquals(AiEditValidationStatus.CONSISTENT, result.status(), id);
            assertTrue(result.editIssues().isEmpty(), id);
            assertEquals(expected.get("requires_review").asBoolean(), result.requiresReview(), id);
            assertEquals(expected.get("auto_place").asBoolean(), result.canAutoPlace(), id);
        }
        switch (id) {
            case "edit-replacement-mismatch-review" -> assertInvalid(
                    results.getFirst(), AiValidationCode.EDIT_REPLACEMENT_MISMATCH);
            case "replace-with-guidance-review" -> assertInvalid(
                    results.getFirst(), AiValidationCode.INVALID_ACTION_FIELDS);
            case "insert-changes-source-review" -> assertInvalid(
                    results.getFirst(), AiValidationCode.INSERT_SOURCE_CHANGED);
            case "anchor-not-found-review", "noncontiguous-anchors-review" -> {
                assertEquals(
                        AiEditValidationStatus.SOURCE_UNRESOLVED,
                        results.getFirst().status());
                assertTrue(results.getFirst().editIssues().isEmpty());
                assertFalse(results.getFirst().canAutoPlace());
            }
            default -> {
                // 영역·레벨·개수 등의 최종 처리는 후속 검증 단계에서 수행한다.
            }
        }
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
    @MethodSource("invalidActionFields")
    void checksEachActionFieldCombination(String fixtureId, String field, String value) throws IOException {
        JsonNode fixture = fixtureCase(fixtureId);
        ObjectNode root =
                (ObjectNode) JSON.readTree(resource(fixture.get("response_file").asString()));
        ((ObjectNode) root.at("/items/0")).set(field, JSON.readTree(value));
        AiEditValidationResult result = validator
                .validate(locations.validate(parser.parse(root.toString()), context(fixture)))
                .getFirst();
        assertInvalid(result, AiValidationCode.INVALID_ACTION_FIELDS);
        assertTrue(
                result.editIssues().stream().anyMatch(issue -> issue.field().equals(field)),
                result.editIssues().toString());
    }

    static Stream<Arguments> invalidActionFields() {
        String edit = "{\"operation\":\"INSERT\",\"text\":\"a\",\"position\":\"BEFORE\"}";
        return Stream.of(
                Arguments.of("standard-insert-before", "feedback_type", "null"),
                Arguments.of("standard-insert-before", "area", "null"),
                Arguments.of("standard-insert-before", "replacement", "null"),
                Arguments.of("standard-insert-before", "guidance", "\"Add a reason.\""),
                Arguments.of("standard-insert-before", "revision_scope", "null"),
                Arguments.of("standard-insert-before", "edit", "null"),
                Arguments.of("standard-insert-before", "reason", "\" \""),
                Arguments.of("advanced-content-guidance", "feedback_type", "null"),
                Arguments.of("advanced-content-guidance", "area", "\"GRAMMAR_SPELLING\""),
                Arguments.of("advanced-content-guidance", "replacement", "\"new content\""),
                Arguments.of("advanced-content-guidance", "guidance", "null"),
                Arguments.of("advanced-content-guidance", "guidance", "\"\""),
                Arguments.of("advanced-content-guidance", "guidance", "\" \""),
                Arguments.of("advanced-content-guidance", "revision_scope", "\"WORD\""),
                Arguments.of("advanced-content-guidance", "edit", edit),
                Arguments.of("review-image-problem", "feedback_type", "\"CORRECTION\""),
                Arguments.of("review-image-problem", "area", "\"GRAMMAR_SPELLING\""),
                Arguments.of("review-image-problem", "replacement", "\"new content\""),
                Arguments.of("review-image-problem", "guidance", "\"Add a reason.\""),
                Arguments.of("review-image-problem", "revision_scope", "\"WORD\""),
                Arguments.of("review-image-problem", "edit", edit),
                Arguments.of("review-image-problem", "target_boxes", "[[0.1,0.2,0.3,0.4]]"),
                Arguments.of("review-image-problem", "location_uncertain", "false"));
    }

    @ParameterizedTest
    @MethodSource("consistentInsertions")
    void matchesExactInsertionOrOneBoundarySpaceWithoutChangingTheSource(
            String source, String replacement, String text, AiEditPosition position) {
        assertConsistent(check(source, AiEditOperation.INSERT, replacement, text, position));
    }

    static Stream<Arguments> consistentInsertions() {
        return Stream.of(
                Arguments.of("dog", "a dog", "a", AiEditPosition.BEFORE),
                Arguments.of("dog", "a small dog", "a small", AiEditPosition.BEFORE),
                Arguments.of("apples", "apples.", ".", AiEditPosition.AFTER),
                Arguments.of("walk", "walked", "ed", AiEditPosition.AFTER),
                Arguments.of("dog", "dog today", "today", AiEditPosition.AFTER),
                Arguments.of(" dog", "a dog", "a", AiEditPosition.BEFORE),
                Arguments.of("dog ", "dog today", "today", AiEditPosition.AFTER),
                Arguments.of("dog", "dog's", "'s", AiEditPosition.AFTER));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", " a", "a ", "\ta", "a\n", "a\u00A0"})
    void rejectsEmptyOrPaddedInsertionText(String text) {
        assertInvalid(
                check("dog", AiEditOperation.INSERT, "a dog", text, AiEditPosition.BEFORE),
                AiValidationCode.INVALID_EDIT_TEXT);
    }

    @ParameterizedTest
    @ValueSource(strings = {"a  dog", "a\tdog", "a\ndog", "a\u00A0dog", "a brown dog", "dog"})
    void rejectsUndeclaredInsertionTextOrArbitrarySeparatorWhitespace(String replacement) {
        assertInvalid(
                check("dog", AiEditOperation.INSERT, replacement, "a", AiEditPosition.BEFORE),
                AiValidationCode.INSERT_TEXT_MISMATCH);
    }

    @ParameterizedTest
    @ValueSource(strings = {"a cat", "dog a"})
    void rejectsChangedSourceOrWrongInsertionSide(String replacement) {
        assertInvalid(
                check("dog", AiEditOperation.INSERT, replacement, "a", AiEditPosition.BEFORE),
                AiValidationCode.INSERT_SOURCE_CHANGED);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " "})
    void rejectsEmptyInsertionReplacement(String replacement) {
        assertInvalid(
                check("dog", AiEditOperation.INSERT, replacement, "a", AiEditPosition.BEFORE),
                AiValidationCode.INVALID_REPLACEMENT);
    }

    @Test
    void doesNotDuplicateWhitespaceAlreadyPresentAtTheSourceBoundary() {
        assertInvalid(
                check(" dog", AiEditOperation.INSERT, "a  dog", "a", AiEditPosition.BEFORE),
                AiValidationCode.INSERT_TEXT_MISMATCH);
        assertInvalid(
                check("dog ", AiEditOperation.INSERT, "dog  today", "today", AiEditPosition.AFTER),
                AiValidationCode.INSERT_TEXT_MISMATCH);
    }

    @Test
    void requiresInsertPositionAndNullPositionForOtherOperations() {
        assertInvalid(check("dog", AiEditOperation.INSERT, "a dog", "a", null), AiValidationCode.INVALID_EDIT_POSITION);
        for (AiEditOperation operation :
                List.of(AiEditOperation.DELETE, AiEditOperation.REPLACE, AiEditOperation.RESTRUCTURE)) {
            for (AiEditPosition position : AiEditPosition.values()) {
                String replacement = operation == AiEditOperation.DELETE ? "" : "replacement";
                assertInvalid(
                        check("word", operation, replacement, replacement, position),
                        AiValidationCode.INVALID_EDIT_POSITION);
            }
        }
    }

    @Test
    void checksDeletionEmptyStringsAndMatchingNonemptyReplacements() {
        assertConsistent(check("to", AiEditOperation.DELETE, "", "", null));
        assertInvalid(check("to", AiEditOperation.DELETE, " ", "", null), AiValidationCode.INVALID_REPLACEMENT);
        assertInvalid(check("to", AiEditOperation.DELETE, "", " ", null), AiValidationCode.INVALID_EDIT_TEXT);
        for (AiEditOperation operation : List.of(AiEditOperation.REPLACE, AiEditOperation.RESTRUCTURE)) {
            assertInvalid(check("word", operation, "", "", null), AiValidationCode.INVALID_REPLACEMENT);
            assertInvalid(check("word", operation, " ", " ", null), AiValidationCode.INVALID_REPLACEMENT);
            assertInvalid(
                    check("word", operation, "replacement", "different", null),
                    AiValidationCode.EDIT_REPLACEMENT_MISMATCH);
            assertInvalid(check("word", operation, "word", "word", null), AiValidationCode.NO_OP_REPLACEMENT);
        }
        AiEditValidationResult spaced = check(" dog ", AiEditOperation.REPLACE, " a dog ", " a dog ", null);
        assertConsistent(spaced);
        assertEquals(" a dog ", spaced.location().original().replacement());
    }

    @Test
    void doesNotUseUnverifiedRawAnchorsAsSourceOrHideIndependentEditErrors() throws IOException {
        JsonNode fixture = fixtureCase("anchor-not-found-review");
        ObjectNode root =
                (ObjectNode) JSON.readTree(resource(fixture.get("response_file").asString()));
        AiLocationValidationResult location = locations
                .validate(parser.parse(root.toString()), context(fixture))
                .getFirst();
        AiEditValidationResult unverified =
                validator.validate(List.of(location)).getFirst();
        assertEquals(AiEditValidationStatus.SOURCE_UNRESOLVED, unverified.status());
        assertTrue(unverified.editIssues().isEmpty());
        assertTrue(unverified.requiresReview());
        assertFalse(unverified.canAutoPlace());
        assertTrue(unverified.issues().stream().anyMatch(issue -> issue.code() == AiValidationCode.ANCHOR_NOT_FOUND));

        ((ObjectNode) root.at("/items/0/edit")).put("text", "went");
        AiEditValidationResult mismatch = validator
                .validate(locations.validate(parser.parse(root.toString()), context(fixture)))
                .getFirst();
        assertInvalid(mismatch, AiValidationCode.EDIT_REPLACEMENT_MISMATCH);
        assertTrue(mismatch.issues().stream().anyMatch(issue -> issue.code() == AiValidationCode.ANCHOR_NOT_FOUND));
    }

    @Test
    void preservesLocationWarningsAndDoesNotRunLaterPolicies() throws IOException {
        for (String id : List.of("uncertain-location-card", "partial-invalid-boxes")) {
            AiEditValidationResult result = fixture(id);
            assertEquals(AiEditValidationStatus.CONSISTENT, result.status());
            assertTrue(result.requiresReview());
            assertTrue(result.editIssues().isEmpty());
            assertEquals(result.location().canAutoPlace(), result.canAutoPlace());
        }
        for (String id : List.of(
                "standard-suggestion-excluded",
                "disabled-area-excluded",
                "grammar-sentence-scope-review",
                "guidance-over-ten-words-review")) {
            assertEquals(AiEditValidationStatus.CONSISTENT, fixture(id).status(), id);
        }
        JsonNode limit = fixtureCase("standard-eight-corrections-limit");
        AiCorrectionResponse response =
                parser.parse(resource(limit.get("response_file").asString()));
        assertEquals(
                8,
                validator.validate(locations.validate(response, context(limit))).size());
        AiEditValidationResult review = fixture("review-image-problem");
        assertEquals(AiEditValidationStatus.CONSISTENT, review.status());
        assertTrue(review.requiresReview());
        assertFalse(review.canAutoPlace());
    }

    @Test
    void returnsImmutableResultsAndSupportsNoCandidates() {
        AiEditValidationResult invalid = check("dog", AiEditOperation.INSERT, "a cat", "a", AiEditPosition.BEFORE);
        assertThrows(
                UnsupportedOperationException.class, () -> invalid.editIssues().clear());
        assertThrows(UnsupportedOperationException.class, () -> invalid.issues().clear());
        List<AiLocationValidationResult> input = new ArrayList<>(List.of(invalid.location()));
        List<AiEditValidationResult> output = validator.validate(input);
        input.clear();
        assertEquals(1, output.size());
        assertSame(invalid.location(), output.getFirst().location());
        assertThrows(UnsupportedOperationException.class, output::clear);
        assertTrue(validator.validate(List.of()).isEmpty());
        assertThrows(NullPointerException.class, () -> validator.validate(null));
    }

    private AiEditValidationResult check(
            String source, AiEditOperation operation, String replacement, String text, AiEditPosition position) {
        boolean restructure = operation == AiEditOperation.RESTRUCTURE;
        AiCorrectionItem item = new AiCorrectionItem(
                AiAction.REPLACE,
                AiFeedbackType.CORRECTION,
                restructure ? AiArea.SENTENCE_STRUCTURE : AiArea.GRAMMAR_SPELLING,
                List.of(restructure ? AiIssueKind.SENTENCE_STRUCTURE : AiIssueKind.GRAMMAR),
                List.of(new AiAnchor(1, source, 1)),
                replacement,
                null,
                "수정 동작 검사용 후보입니다.",
                false,
                BOX,
                restructure ? AiRevisionScope.SENTENCE : AiRevisionScope.WORD,
                new AiEdit(operation, text, position));
        return validator
                .validate(locations.validate(
                        new AiCorrectionResponse(List.of(item)), AiLocationContext.fromText(1, source)))
                .getFirst();
    }

    private AiEditValidationResult fixture(String id) throws IOException {
        JsonNode fixture = fixtureCase(id);
        AiCorrectionResponse response =
                parser.parse(resource(fixture.get("response_file").asString()));
        return validator
                .validate(locations.validate(response, context(fixture)))
                .getFirst();
    }

    private static AiLocationContext context(JsonNode fixture) {
        List<String> lines = new ArrayList<>();
        fixture.at("/context/transcription_lines")
                .forEach(line -> lines.add(line.get("text").asString()));
        return new AiLocationContext(fixture.at("/context/page_number").intValue(), lines);
    }

    private static JsonNode fixtureCase(String id) throws IOException {
        for (JsonNode fixture : JSON.readTree(resource("cases.json")).get("cases")) {
            if (fixture.get("id").asString().equals(id)) return fixture;
        }
        throw new IllegalArgumentException("Unknown fixture: " + id);
    }

    private static void assertConsistent(AiEditValidationResult result) {
        assertEquals(
                AiEditValidationStatus.CONSISTENT,
                result.status(),
                result.issues().toString());
        assertTrue(result.editIssues().isEmpty());
    }

    private static void assertInvalid(AiEditValidationResult result, AiValidationCode code) {
        assertEquals(AiEditValidationStatus.INCONSISTENT, result.status());
        assertTrue(
                result.editIssues().stream().anyMatch(issue -> issue.code() == code),
                result.editIssues().toString());
        assertTrue(result.requiresReview());
        assertFalse(result.canAutoPlace());
    }

    private static String resource(String relative) throws IOException {
        try (var input = AiEditValidatorTest.class.getResourceAsStream(ROOT + relative)) {
            assertNotNull(input, relative);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
