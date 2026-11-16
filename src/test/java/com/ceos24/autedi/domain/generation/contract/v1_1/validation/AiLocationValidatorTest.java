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
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 고정 전사와 후보만 사용한다. AI·Spring·DB·정상 카드 정책은 실행하지 않는다. */
class AiLocationValidatorTest {
    private static final String ROOT = "/ai/contract/v1.1/";
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final List<List<Double>> BOX = List.of(List.of(0.10, 0.20, 0.40, 0.25));
    private final AiLocationValidator validator = new AiLocationValidator();
    private final AiCorrectionResponseParser parser = new AiCorrectionResponseParser();

    @ParameterizedTest(name = "{0}")
    @MethodSource("readableFixtures")
    void checksFixtureLocationsWithoutClassifyingOrDiscardingCandidates(String id, JsonNode fixture)
            throws IOException {
        AiCorrectionResponse response =
                parser.parse(resource(fixture.get("response_file").asString()));
        List<String> lines = new ArrayList<>();
        fixture.at("/context/transcription_lines")
                .forEach(line -> lines.add(line.get("text").asString()));
        AiLocationContext context =
                new AiLocationContext(fixture.at("/context/page_number").intValue(), lines);
        JsonNode before = JSON.valueToTree(response);
        List<AiLocationValidationResult> results = validator.validate(response, context);
        assertEquals(response.items().size(), results.size(), id);
        assertEquals(before, JSON.valueToTree(response), id);
        assertEquals(lines, context.transcriptionLines(), id);
        for (int index = 0; index < results.size(); index++) {
            AiLocationValidationResult result = results.get(index);
            assertEquals(context.pageNumber(), result.pageNumber(), id);
            assertEquals(index, result.candidateIndex(), id);
            assertSame(response.items().get(index), result.original(), id);
            if (result.original().action() == AiAction.REVIEW) {
                assertTrue(result.requiresReview(), id);
                assertFalse(result.canAutoPlace(), id);
            }
        }
        for (JsonNode expected : fixture.at("/expected/cards")) {
            AiLocationValidationResult result =
                    results.get(expected.get("candidate_index").intValue());
            assertTrue(result.sourceResolved(), id);
            assertEquals(expected.get("original_text").asString(), result.originalText(), id);
            List<AiResolvedAnchor> ranges = new ArrayList<>();
            for (JsonNode anchor : expected.get("resolved_anchors")) {
                ranges.add(new AiResolvedAnchor(
                        anchor.get("line_id").intValue(),
                        anchor.get("start").intValue(),
                        anchor.get("end").intValue()));
            }
            assertEquals(ranges, result.resolvedAnchors(), id);
            assertEquals(boxes(expected.get("valid_target_boxes")), result.validTargetBoxes(), id);
            assertEquals(expected.get("requires_review").asBoolean(), result.requiresReview(), id);
            assertEquals(expected.get("auto_place").asBoolean(), result.canAutoPlace(), id);
        }
        if (id.equals("anchor-not-found-review")) {
            assertUnresolved(results.getFirst(), AiValidationCode.ANCHOR_NOT_FOUND);
        } else if (id.equals("noncontiguous-anchors-review")) {
            assertUnresolved(results.getFirst(), AiValidationCode.ANCHORS_NOT_CONTIGUOUS);
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

    @Test
    void resolvesNonOverlappingOccurrencesAndActualUtf16Indices() {
        AiLocationValidationResult repeated =
                check(List.of("aaaaaa"), item(List.of(new AiAnchor(1, "aa", 3)), BOX, false, AiAction.REPLACE));
        assertEquals(List.of(new AiResolvedAnchor(1, 4, 6)), repeated.resolvedAnchors());
        AiLocationValidationResult overlapping =
                check(List.of("aaa"), item(List.of(new AiAnchor(1, "aa", 2)), BOX, false, AiAction.REPLACE));
        assertUnresolved(overlapping, AiValidationCode.ANCHOR_NOT_FOUND);

        AiLocationValidationResult unicode =
                check(List.of("😀 dog"), item(List.of(new AiAnchor(1, "dog", 1)), BOX, false, AiAction.REPLACE));
        assertEquals(List.of(new AiResolvedAnchor(1, 3, 6)), unicode.resolvedAnchors());
        AiLocationValidationResult emoji =
                check(List.of("😀 dog"), item(List.of(new AiAnchor(1, "😀", 1)), BOX, false, AiAction.REPLACE));
        assertEquals(List.of(new AiResolvedAnchor(1, 0, 2)), emoji.resolvedAnchors());
    }

    @ParameterizedTest
    @MethodSource("invalidAnchors")
    void doesNotGuessForMissingLinesOccurrencesOrNormalizedText(AiAnchor anchor) {
        AiCorrectionItem item = item(List.of(anchor), BOX, false, AiAction.REPLACE);
        AiLocationValidationResult result = check(List.of("Dog, dog."), item);
        assertUnresolved(result, AiValidationCode.ANCHOR_NOT_FOUND);
        assertSame(item, result.original());
        assertEquals(BOX, result.original().targetBoxes());
    }

    static Stream<Arguments> invalidAnchors() {
        return Stream.of(
                Arguments.of(new AiAnchor(0, "dog", 1)),
                Arguments.of(new AiAnchor(2, "dog", 1)),
                Arguments.of(new AiAnchor(Integer.MAX_VALUE, "dog", 1)),
                Arguments.of(new AiAnchor(1, "dog", 0)),
                Arguments.of(new AiAnchor(1, "dog", Integer.MAX_VALUE)),
                Arguments.of(new AiAnchor(1, "DOG", 1)),
                Arguments.of(new AiAnchor(1, "Dog dog", 1)),
                Arguments.of(new AiAnchor(1, " dog ", 1)),
                Arguments.of(new AiAnchor(1, "", 1)),
                Arguments.of(new AiAnchor(1, null, 1)),
                Arguments.of(new AiAnchor(1, "Dog\ndog", 1)));
    }

    @Test
    void preservesTheSelectedWhitespaceAndNeverSearchesAnotherLine() {
        AiLocationValidationResult exact =
                check(List.of(" dog "), item(List.of(new AiAnchor(1, " dog ", 1)), BOX, false, AiAction.REPLACE));
        assertEquals(" dog ", exact.originalText());
        assertEquals(List.of(new AiResolvedAnchor(1, 0, 5)), exact.resolvedAnchors());
        assertUnresolved(
                check(List.of("cat", "dog"), item(List.of(new AiAnchor(1, "dog", 1)), BOX, false, AiAction.REPLACE)),
                AiValidationCode.ANCHOR_NOT_FOUND);
    }

    @Test
    void rejectsHalfOfASurrogatePairInsteadOfExposingABrokenCharacterRange() {
        for (String half : List.of("\uD83D", "\uDE00")) {
            AiLocationValidationResult result =
                    check(List.of("😀 dog"), item(List.of(new AiAnchor(1, half, 1)), BOX, false, AiAction.REPLACE));
            assertUnresolved(result, AiValidationCode.ANCHOR_SPLITS_SURROGATE_PAIR);
        }
    }

    @Test
    void resolvesSuffixWholeMiddleAndPrefixAsOneContinuousSource() {
        List<AiAnchor> anchors =
                List.of(new AiAnchor(1, "alpha", 1), new AiAnchor(2, "middle beta", 1), new AiAnchor(3, "gamma", 1));
        AiLocationValidationResult result =
                check(List.of("first alpha", "middle beta", "gamma last"), item(anchors, BOX, false, AiAction.REPLACE));
        assertEquals("alpha\nmiddle beta\ngamma", result.originalText());
        assertEquals(
                List.of(new AiResolvedAnchor(1, 6, 11), new AiResolvedAnchor(2, 0, 11), new AiResolvedAnchor(3, 0, 5)),
                result.resolvedAnchors());
        assertEquals(1, result.validTargetBoxes().size());
        assertFalse(result.requiresReview());
    }

    @ParameterizedTest
    @MethodSource("invalidBoundaries")
    void rejectsPartialBoundariesWithoutReturningPartialPositions(List<AiAnchor> anchors) {
        AiLocationValidationResult result =
                check(List.of("first alpha", "middle beta", "gamma last"), item(anchors, BOX, false, AiAction.REPLACE));
        assertUnresolved(result, AiValidationCode.ANCHOR_BOUNDARY_MISMATCH);
    }

    static Stream<Arguments> invalidBoundaries() {
        return Stream.of(
                Arguments.of(List.of(new AiAnchor(1, "first", 1), new AiAnchor(2, "middle", 1))),
                Arguments.of(List.of(new AiAnchor(1, "alpha", 1), new AiAnchor(2, "beta", 1))),
                Arguments.of(List.of(
                        new AiAnchor(1, "alpha", 1), new AiAnchor(2, "middle", 1), new AiAnchor(3, "gamma", 1))),
                Arguments.of(List.of(
                        new AiAnchor(1, "alpha", 1), new AiAnchor(2, "middle beta", 1), new AiAnchor(3, "last", 1))));
    }

    @ParameterizedTest
    @MethodSource("noncontiguousAnchors")
    void rejectsRepeatedReversedAndSkippedLinesWithoutSortingThem(List<AiAnchor> anchors) {
        AiLocationValidationResult result =
                check(List.of("one", "two", "three"), item(anchors, BOX, false, AiAction.REPLACE));
        assertUnresolved(result, AiValidationCode.ANCHORS_NOT_CONTIGUOUS);
        assertEquals(anchors, result.original().anchors());
    }

    static Stream<Arguments> noncontiguousAnchors() {
        return Stream.of(
                Arguments.of(List.of(new AiAnchor(1, "one", 1), new AiAnchor(1, "one", 1))),
                Arguments.of(List.of(new AiAnchor(2, "two", 1), new AiAnchor(1, "one", 1))),
                Arguments.of(List.of(new AiAnchor(1, "one", 1), new AiAnchor(3, "three", 1))));
    }

    @Test
    void refusesParagraphBoundariesAndFailedSegmentsAtomically() {
        AiLocationValidationResult blank = check(
                List.of("one", "", "three"),
                item(List.of(new AiAnchor(1, "one", 1), new AiAnchor(3, "three", 1)), BOX, false, AiAction.REPLACE));
        assertUnresolved(blank, AiValidationCode.PARAGRAPH_BOUNDARY);
        AiLocationValidationResult whitespace = check(
                List.of("one", "  ", "three"),
                item(
                        List.of(new AiAnchor(1, "one", 1), new AiAnchor(2, "  ", 1), new AiAnchor(3, "three", 1)),
                        BOX,
                        false,
                        AiAction.REPLACE));
        assertUnresolved(whitespace, AiValidationCode.PARAGRAPH_BOUNDARY);
        AiLocationValidationResult missing = check(
                List.of("one", "two"),
                item(List.of(new AiAnchor(1, "one", 1), new AiAnchor(2, "missing", 1)), BOX, false, AiAction.REPLACE));
        assertUnresolved(missing, AiValidationCode.ANCHOR_NOT_FOUND);
        assertHas(missing, AiValidationCode.BOX_WITHOUT_ANCHORS);
    }

    @ParameterizedTest
    @MethodSource("invalidBoxes")
    void dropsInvalidBoxesWithoutClampingOrChangingTheOriginal(List<Double> box) {
        AiLocationValidationResult result = check(
                List.of("word"), item(List.of(new AiAnchor(1, "word", 1)), List.of(box), false, AiAction.REPLACE));
        assertTrue(result.sourceResolved());
        assertEquals(List.of(box), result.original().targetBoxes());
        assertTrue(result.validTargetBoxes().isEmpty());
        assertHas(result, AiValidationCode.INVALID_TARGET_BOX);
        assertHas(result, AiValidationCode.NO_VALID_TARGET_BOXES);
        assertTrue(result.requiresReview());
        assertFalse(result.canAutoPlace());
    }

    static Stream<Arguments> invalidBoxes() {
        return Stream.of(
                Arguments.of(List.of(0.5, 0.2, 0.5, 0.4)),
                Arguments.of(List.of(0.2, 0.5, 0.4, 0.5)),
                Arguments.of(List.of(0.8, 0.2, 0.3, 0.4)),
                Arguments.of(List.of(0.2, 0.8, 0.4, 0.3)),
                Arguments.of(List.of(-0.1, 0.2, 0.4, 0.5)),
                Arguments.of(List.of(0.1, 0.2, 1.1, 0.5)),
                Arguments.of(List.of(Double.NaN, 0.2, 0.4, 0.5)),
                Arguments.of(List.of(0.1, Double.POSITIVE_INFINITY, 0.4, 0.5)),
                Arguments.of(List.of(0.1, 0.2, 0.3)),
                Arguments.of(List.of(0.1, 0.2, 0.3, 0.4, 0.5)));
    }

    @Test
    void acceptsImageEdgesAndMoreThanOneBoxForOneAnchor() {
        List<List<Double>> boxes = List.of(List.of(0.0, 0.0, 1.0, 1.0), List.of(0.1, 0.2, 0.3, 0.4));
        AiLocationValidationResult result =
                check(List.of("word"), item(List.of(new AiAnchor(1, "word", 1)), boxes, false, AiAction.REPLACE));
        assertEquals(boxes, result.validTargetBoxes());
        assertFalse(result.requiresReview());
        assertTrue(result.canAutoPlace());
    }

    @Test
    void preservesUncertaintyEvenWhenThereAreValidCoordinates() {
        AiLocationValidationResult result =
                check(List.of("word"), item(List.of(new AiAnchor(1, "word", 1)), BOX, true, AiAction.REPLACE));
        assertEquals(BOX, result.validTargetBoxes());
        assertHas(result, AiValidationCode.MODEL_LOCATION_UNCERTAIN);
        assertTrue(result.requiresReview());
        assertFalse(result.canAutoPlace());
        AiLocationValidationResult inconsistent =
                check(List.of("word"), item(List.of(new AiAnchor(1, "word", 1)), List.of(), false, AiAction.REPLACE));
        assertHas(inconsistent, AiValidationCode.NO_VALID_TARGET_BOXES);
        assertHas(inconsistent, AiValidationCode.LOCATION_FLAG_INCONSISTENT);
        assertFalse(inconsistent.original().locationUncertain());
        assertFalse(inconsistent.canAutoPlace());
    }

    @Test
    void distinguishesMissingOrdinaryAnchorsFromLocationlessReview() {
        AiLocationValidationResult ordinary = check(List.of("word"), item(List.of(), BOX, false, AiAction.REPLACE));
        assertUnresolved(ordinary, AiValidationCode.ANCHORS_REQUIRED);
        AiLocationValidationResult review = check(List.of("word"), item(List.of(), List.of(), true, AiAction.REVIEW));
        assertFalse(review.sourceResolved());
        assertTrue(review.requiresReview());
        assertFalse(review.canAutoPlace());
        assertFalse(review.issues().stream().anyMatch(issue -> issue.code() == AiValidationCode.ANCHORS_REQUIRED));
        AiLocationValidationResult anchoredReview =
                check(List.of("word"), item(List.of(new AiAnchor(1, "word", 1)), BOX, false, AiAction.REVIEW));
        assertTrue(anchoredReview.sourceResolved());
        assertTrue(anchoredReview.requiresReview());
        assertFalse(anchoredReview.canAutoPlace());
    }

    @Test
    void doesNotApplyLevelCardLimitOrReorderTheCandidates() throws IOException {
        AiCorrectionResponse response = parser.parse(resource("responses/standard-eight-corrections-limit.json"));
        List<String> lines = List.of(
                "She go to school.",
                "He play tennis.",
                "We was late.",
                "They is happy.",
                "She have a dog.",
                "He do his homework.",
                "I has a pen.",
                "It look nice.");
        List<AiLocationValidationResult> results = validator.validate(response, new AiLocationContext(1, lines));
        assertEquals(8, results.size());
        assertTrue(results.stream().allMatch(AiLocationValidationResult::sourceResolved));
        assertEquals(7, results.getLast().candidateIndex());
        AiCorrectionResponse suggestion = parser.parse(resource("responses/standard-suggestion-excluded.json"));
        List<AiLocationValidationResult> located = validator.validate(
                suggestion,
                new AiLocationContext(1, List.of("I finished my homework.", "I took a shower.", "I went to bed.")));
        assertTrue(located.getFirst().sourceResolved());
        assertFalse(located.getFirst().requiresReview());
    }

    @Test
    void returnsImmutableResultsAndAllowsAnEmptyResponse() {
        AiCorrectionItem item = item(List.of(new AiAnchor(1, "word", 1)), BOX, false, AiAction.REPLACE);
        List<AiLocationValidationResult> results =
                validator.validate(new AiCorrectionResponse(List.of(item)), new AiLocationContext(1, List.of("word")));
        AiLocationValidationResult result = results.getFirst();
        assertThrows(UnsupportedOperationException.class, results::clear);
        assertThrows(UnsupportedOperationException.class, () -> result.resolvedAnchors()
                .clear());
        assertThrows(UnsupportedOperationException.class, () -> result.validTargetBoxes()
                .clear());
        assertThrows(
                UnsupportedOperationException.class,
                () -> result.validTargetBoxes().getFirst().set(0, 0.9));
        assertThrows(UnsupportedOperationException.class, () -> result.issues().clear());
        assertTrue(validator
                .validate(new AiCorrectionResponse(List.of()), new AiLocationContext(1, List.of("word")))
                .isEmpty());
    }

    private AiLocationValidationResult check(List<String> lines, AiCorrectionItem item) {
        return validator
                .validate(new AiCorrectionResponse(List.of(item)), new AiLocationContext(1, lines))
                .getFirst();
    }

    private static AiCorrectionItem item(
            List<AiAnchor> anchors, List<List<Double>> boxes, boolean uncertain, AiAction action) {
        boolean review = action == AiAction.REVIEW;
        return new AiCorrectionItem(
                action,
                review ? null : AiFeedbackType.CORRECTION,
                review ? null : AiArea.GRAMMAR_SPELLING,
                List.of(review ? AiIssueKind.ILLEGIBLE : AiIssueKind.GRAMMAR),
                anchors,
                review ? null : "replacement",
                null,
                "위치 검사용 합성 후보입니다.",
                uncertain,
                boxes,
                review ? null : AiRevisionScope.PHRASE,
                review ? null : new AiEdit(AiEditOperation.REPLACE, "replacement", null));
    }

    private static void assertUnresolved(AiLocationValidationResult result, AiValidationCode code) {
        assertFalse(result.sourceResolved());
        assertNull(result.originalText());
        assertTrue(result.resolvedAnchors().isEmpty());
        assertTrue(result.validTargetBoxes().isEmpty());
        assertTrue(result.requiresReview());
        assertFalse(result.canAutoPlace());
        assertHas(result, code);
    }

    private static void assertHas(AiLocationValidationResult result, AiValidationCode code) {
        assertTrue(
                result.issues().stream().anyMatch(issue -> issue.code() == code),
                result.issues().toString());
    }

    private static List<List<Double>> boxes(JsonNode values) {
        List<List<Double>> boxes = new ArrayList<>();
        for (JsonNode value : values) {
            List<Double> box = new ArrayList<>();
            value.forEach(number -> box.add(number.doubleValue()));
            boxes.add(box);
        }
        return boxes;
    }

    private static String resource(String relative) throws IOException {
        try (var input = AiLocationValidatorTest.class.getResourceAsStream(ROOT + relative)) {
            assertNotNull(input, relative);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
