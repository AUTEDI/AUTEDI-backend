package com.ceos24.autedi.domain.generation.contract.v1.validation;

import static org.junit.jupiter.api.Assertions.*;

import com.ceos24.autedi.domain.generation.contract.v1.AiAction;
import com.ceos24.autedi.domain.generation.contract.v1.AiAnchor;
import com.ceos24.autedi.domain.generation.contract.v1.AiArea;
import com.ceos24.autedi.domain.generation.contract.v1.AiCorrectionItem;
import com.ceos24.autedi.domain.generation.contract.v1.AiCorrectionResponse;
import com.ceos24.autedi.domain.generation.contract.v1.AiCorrectionResponseParser;
import com.ceos24.autedi.domain.generation.contract.v1.AiFeedbackType;
import com.ceos24.autedi.domain.generation.contract.v1.AiIssueKind;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/** 고정 JSON·객체만 사용한다. DB, Spring context, 실험 main, 외부 API는 실행하지 않는다. */
class AiCorrectionResponseValidatorTest {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final List<List<Double>> BOX = List.of(List.of(0.1, 0.1, 0.2, 0.2));
    private static final Map<AiArea, AiCorrectionLevel> ALL_AREAS = Map.of(
            AiArea.GRAMMAR_SPELLING, AiCorrectionLevel.STANDARD,
            AiArea.VOCABULARY_EXPRESSION, AiCorrectionLevel.ADVANCED,
            AiArea.SENTENCE_STRUCTURE, AiCorrectionLevel.ADVANCED,
            AiArea.CONTENT_SUMMARY, AiCorrectionLevel.STANDARD);
    private final AiCorrectionResponseParser parser = new AiCorrectionResponseParser();
    private final AiCorrectionResponseValidator validator = new AiCorrectionResponseValidator();

    @Test
    void partitionsFixedCandidatesAndPreservesOriginalsReasonsAndSourceOrder() throws IOException {
        String raw = fixture("postprocessing-candidates.json");
        AiCorrectionResponse response = parser.parse(raw);
        AiValidationContext context = AiValidationContext.fromText(
                2,
                "😀 go then go\r\n\r\nI like this book.\r\n",
                Map.of(
                        AiArea.GRAMMAR_SPELLING, AiCorrectionLevel.STANDARD,
                        AiArea.CONTENT_SUMMARY, AiCorrectionLevel.ADVANCED));

        AiValidationResult result = validator.validate(response, context);
        assertEquals(8, result.candidates().size());
        assertEquals(4, result.corrections().size());
        assertEquals(3, result.reviewIssues().size());
        assertEquals(1, result.excludedItems().size());
        assertEquals(1, result.omittedCount());
        assertEquals(
                List.of(7, 6, 0, 1),
                result.corrections().stream()
                        .map(AiValidatedItem::candidateIndex)
                        .toList());
        assertEquals(
                IntStream.range(0, 8).boxed().toList(),
                result.candidates().stream()
                        .map(AiValidatedItem::candidateIndex)
                        .toList());
        assertSame(response, result.originalResponse());
        assertSame(context, result.context());
        for (AiValidatedItem item : result.candidates()) {
            assertSame(response.items().get(item.candidateIndex()), item.original());
            assertEquals(2, item.pageNumber());
        }
        assertEquals(new AiResolvedAnchor(1, 11, 13), result.candidates().get(0).resolvedAnchor());
        assertEquals(new AiResolvedAnchor(3, 0, 17), result.candidates().get(1).resolvedAnchor());
        assertFalse(result.candidates().get(0).requiresReview());
        assertHas(result.candidates().get(2), AiValidationCode.MODEL_REVIEW);
        assertHas(result.candidates().get(3), AiValidationCode.ANCHOR_NOT_FOUND);
        assertNull(result.candidates().get(3).resolvedAnchor());
        assertTrue(result.candidates().get(3).validBoxes().isEmpty());
        assertEquals("kept proposal", result.candidates().get(3).original().replacement());
        assertHas(result.candidates().get(4), AiValidationCode.AREA_NOT_SELECTED);
        assertHas(result.candidates().get(5), AiValidationCode.INVALID_FIELD_COMBINATION);
        assertEquals("", result.candidates().get(6).original().replacement());
        assertHas(result.candidates().get(6), AiValidationCode.NO_VALID_BOXES);
        assertEquals(BOX, result.candidates().get(7).validBoxes());
        assertEquals(2, result.candidates().get(7).original().boxes().size());
        assertTrue(result.candidates().get(7).issues().stream()
                .anyMatch(issue -> issue.code() == AiValidationCode.INVALID_BOX
                        && issue.field().equals("boxes[1]")));
        assertEquals("😀 go then go", result.context().transcriptionLines().getFirst());
        assertEquals(
                List.of("😀 go then go", "", "I like this book.", ""),
                result.context().transcriptionLines());
        assertEquals(response, parser.parse(raw));
        assertEquals(result, validator.validate(response, context));
    }

    @ParameterizedTest
    @MethodSource("invalidCombinations")
    void sendsInvalidActionCombinationsToReviewWithoutDroppingProposals(
            int index, String field, String value, AiValidationCode code) throws IOException {
        ObjectNode raw = (ObjectNode) JSON.readTree(fixture("correction-response.json"));
        ((ObjectNode) raw.get("items").get(index)).set(field, JSON.readTree(value));
        AiCorrectionResponse response = parser.parse(raw.toString());
        AiValidationContext context =
                AiValidationContext.fromText(1, "He go to school yesterday.\nI like this book.", ALL_AREAS);
        AiValidatedItem checked =
                validator.validate(response, context).candidates().get(index);
        assertEquals(AiItemDisposition.REVIEW, checked.disposition());
        assertHas(checked, code);
        assertSame(response.items().get(index), checked.original());
    }

    static Stream<Arguments> invalidCombinations() {
        return Stream.of(
                Arguments.of(0, "feedback_type", "null", AiValidationCode.INVALID_FIELD_COMBINATION),
                Arguments.of(0, "area", "null", AiValidationCode.INVALID_FIELD_COMBINATION),
                Arguments.of(0, "anchor", "null", AiValidationCode.INVALID_FIELD_COMBINATION),
                Arguments.of(0, "replacement", "null", AiValidationCode.INVALID_FIELD_COMBINATION),
                Arguments.of(0, "guidance", "\"Add one example.\"", AiValidationCode.INVALID_FIELD_COMBINATION),
                Arguments.of(1, "feedback_type", "null", AiValidationCode.INVALID_FIELD_COMBINATION),
                Arguments.of(1, "area", "\"GRAMMAR_SPELLING\"", AiValidationCode.INVALID_FIELD_COMBINATION),
                Arguments.of(1, "anchor", "null", AiValidationCode.INVALID_FIELD_COMBINATION),
                Arguments.of(1, "replacement", "\"replace\"", AiValidationCode.INVALID_FIELD_COMBINATION),
                Arguments.of(1, "guidance", "null", AiValidationCode.INVALID_FIELD_COMBINATION),
                Arguments.of(1, "guidance", "\"   \"", AiValidationCode.INVALID_FIELD_COMBINATION),
                Arguments.of(
                        1,
                        "guidance",
                        "\"one two three four five six seven eight nine ten eleven\"",
                        AiValidationCode.GUIDANCE_TOO_LONG),
                Arguments.of(1, "guidance", "\"이유를 Add 해주세요.\"", AiValidationCode.GUIDANCE_NOT_ENGLISH),
                Arguments.of(2, "feedback_type", "\"CORRECTION\"", AiValidationCode.INVALID_FIELD_COMBINATION),
                Arguments.of(2, "area", "\"CONTENT_SUMMARY\"", AiValidationCode.INVALID_FIELD_COMBINATION),
                Arguments.of(2, "replacement", "\"\"", AiValidationCode.INVALID_FIELD_COMBINATION),
                Arguments.of(2, "guidance", "\"Add one example.\"", AiValidationCode.INVALID_FIELD_COMBINATION),
                Arguments.of(2, "location_uncertain", "false", AiValidationCode.INVALID_FIELD_COMBINATION),
                Arguments.of(2, "boxes", "[[0,0,1,1]]", AiValidationCode.INVALID_FIELD_COMBINATION),
                Arguments.of(0, "issue_kinds", "[\"ocr_mismatch\"]", AiValidationCode.INVALID_ISSUE_KINDS),
                Arguments.of(2, "issue_kinds", "[\"grammar\"]", AiValidationCode.INVALID_ISSUE_KINDS));
    }

    @ParameterizedTest
    @MethodSource("invalidAnchors")
    void neverFabricatesPositionsForUnmatchedOrInvalidAnchors(AiAnchor anchor) {
        AiCorrectionItem item = replace(anchor, AiArea.GRAMMAR_SPELLING, List.of(AiIssueKind.GRAMMAR), BOX);
        AiValidatedItem checked = checkOne(item, "😀 go then go\n\n");
        assertEquals(AiItemDisposition.REVIEW, checked.disposition());
        assertNull(checked.resolvedAnchor());
        assertTrue(checked.validBoxes().isEmpty());
        assertHas(checked, AiValidationCode.ANCHOR_NOT_FOUND);
        assertEquals(anchor, checked.original().anchor());
    }

    static Stream<Arguments> invalidAnchors() {
        return Stream.of(
                Arguments.of(new AiAnchor(0, "go", 1)),
                Arguments.of(new AiAnchor(4, "go", 1)),
                Arguments.of(new AiAnchor(2, "go", 1)),
                Arguments.of(new AiAnchor(1, "Go", 1)),
                Arguments.of(new AiAnchor(1, "go", 0)),
                Arguments.of(new AiAnchor(1, "go", 3)),
                Arguments.of(new AiAnchor(1, "", 1)),
                Arguments.of(new AiAnchor(1, "go\nthen", 1)));
    }

    @Test
    void occurrenceIsNonOverlappingAndOffsetsAreUtf16WithinTheLine() {
        AiValidatedItem repeated = checkOne(
                replace(new AiAnchor(1, "aa", 2), AiArea.GRAMMAR_SPELLING, List.of(AiIssueKind.GRAMMAR), BOX), "aaaaa");
        assertEquals(new AiResolvedAnchor(1, 2, 4), repeated.resolvedAnchor());
        AiValidatedItem emoji = checkOne(
                replace(new AiAnchor(1, "go", 2), AiArea.GRAMMAR_SPELLING, List.of(AiIssueKind.GRAMMAR), BOX),
                "😀 go then go");
        assertEquals(new AiResolvedAnchor(1, 11, 13), emoji.resolvedAnchor());
        assertEquals(
                "go",
                "😀 go then go"
                        .substring(
                                emoji.resolvedAnchor().start(),
                                emoji.resolvedAnchor().end()));
    }

    @ParameterizedTest
    @MethodSource("invalidBoxes")
    void removesOnlyInvalidBoxesAndRetainsRawBoxAndWarning(List<Double> invalidBox) {
        AiCorrectionItem item = replace(
                new AiAnchor(1, "go", 1),
                AiArea.GRAMMAR_SPELLING,
                List.of(AiIssueKind.GRAMMAR),
                List.of(BOX.getFirst(), invalidBox));
        AiValidatedItem checked = checkOne(item, "go");
        assertEquals(AiItemDisposition.CORRECTION, checked.disposition());
        assertEquals(BOX, checked.validBoxes());
        assertEquals(invalidBox, checked.original().boxes().get(1));
        assertHas(checked, AiValidationCode.INVALID_BOX);
        assertTrue(checked.requiresReview());
    }

    static Stream<Arguments> invalidBoxes() {
        return Stream.of(
                Arguments.of(List.of(0.5, 0.5, 0.5, 0.5)),
                Arguments.of(List.of(0.8, 0.1, 0.2, 0.5)),
                Arguments.of(List.of(0.1, 0.8, 0.5, 0.2)),
                Arguments.of(List.of(-0.1, 0.0, 1.0, 1.0)),
                Arguments.of(List.of(0.0, 0.0, 1.1, 1.0)),
                Arguments.of(List.of(0.0, 0.0, 1.0)),
                Arguments.of(List.of(0.0, 0.0, 1.0, 0.0, 1.0, 1.0, 0.0, 1.0)),
                Arguments.of(List.of(Double.NaN, 0.0, 1.0, 1.0)),
                Arguments.of(List.of(0.0, 0.0, Double.POSITIVE_INFINITY, 1.0)));
    }

    @Test
    void keepsCardWithoutValidCoordinatesAndPreservesModelUncertaintySeparately() {
        AiCorrectionItem item = replace(
                new AiAnchor(1, "go", 1),
                AiArea.GRAMMAR_SPELLING,
                List.of(AiIssueKind.GRAMMAR),
                List.of(List.of(1.0, 1.0, 1.0, 1.0)));
        AiValidatedItem checked = checkOne(item, "go");
        assertEquals(AiItemDisposition.CORRECTION, checked.disposition());
        assertHas(checked, AiValidationCode.NO_VALID_BOXES);
        assertTrue(checked.requiresReview());
        assertFalse(checked.original().locationUncertain());
    }

    @ParameterizedTest
    @EnumSource(
            value = AiIssueKind.class,
            names = {"OCR_MISMATCH", "ILLEGIBLE", "UNCLEAR_INTENT", "IMAGE_PROBLEM"})
    void anchoredReviewsAreRetainedWithoutAnySelectedCorrectionArea(AiIssueKind kind) {
        AiCorrectionItem item = new AiCorrectionItem(
                AiAction.REVIEW,
                null,
                null,
                List.of(kind),
                new AiAnchor(1, "go", 1),
                null,
                null,
                "원문을 확인해 주세요.",
                false,
                BOX);
        AiValidationResult result = validator.validate(
                new AiCorrectionResponse(List.of(item)), AiValidationContext.fromText(1, "go", Map.of()));
        assertTrue(result.corrections().isEmpty());
        assertTrue(result.excludedItems().isEmpty());
        AiValidatedItem review = result.reviewIssues().getFirst();
        assertEquals(new AiResolvedAnchor(1, 0, 2), review.resolvedAnchor());
        assertEquals(BOX, review.validBoxes());
        assertEquals(
                List.of(AiValidationCode.MODEL_REVIEW),
                review.issues().stream().map(AiValidationIssue::code).toList());
        assertTrue(review.requiresReview());
    }

    @ParameterizedTest
    @MethodSource("invalidIssueLists")
    void retainsMalformedObjectIssueListsForReview(List<AiIssueKind> kinds) {
        AiCorrectionItem item = replace(new AiAnchor(1, "go", 1), AiArea.GRAMMAR_SPELLING, kinds, BOX);
        AiValidatedItem checked = checkOne(item, "go");
        assertEquals(AiItemDisposition.REVIEW, checked.disposition());
        assertEquals(kinds, checked.original().issueKinds());
        assertHas(checked, AiValidationCode.INVALID_ISSUE_KINDS);
    }

    static Stream<Arguments> invalidIssueLists() {
        return Stream.of(Arguments.of(List.of()), Arguments.of(List.of(AiIssueKind.GRAMMAR, AiIssueKind.GRAMMAR)));
    }

    @Test
    void modelUncertaintyFlagsAnOtherwiseDisplayableCard() {
        AiCorrectionItem item = new AiCorrectionItem(
                AiAction.REPLACE,
                AiFeedbackType.CORRECTION,
                AiArea.GRAMMAR_SPELLING,
                List.of(AiIssueKind.GRAMMAR),
                new AiAnchor(1, "go", 1),
                "went",
                null,
                "수정 제안입니다.",
                true,
                BOX);
        AiValidatedItem checked = checkOne(item, "go");
        assertEquals(AiItemDisposition.CORRECTION, checked.disposition());
        assertEquals(BOX, checked.validBoxes());
        assertTrue(checked.original().locationUncertain());
        assertHas(checked, AiValidationCode.MODEL_LOCATION_UNCERTAIN);
    }

    @Test
    void exclusionTakesPrecedenceButRetainsAllOtherValidationReasons() {
        AiCorrectionItem item = replace(
                new AiAnchor(1, "missing", 1), AiArea.VOCABULARY_EXPRESSION, List.of(AiIssueKind.VOCABULARY), BOX);
        AiValidationResult result = validator.validate(
                new AiCorrectionResponse(List.of(item)), AiValidationContext.fromText(1, "go", Map.of()));
        AiValidatedItem excluded = result.excludedItems().getFirst();
        assertHas(excluded, AiValidationCode.AREA_NOT_SELECTED);
        assertHas(excluded, AiValidationCode.ANCHOR_NOT_FOUND);
        assertHas(excluded, AiValidationCode.BOX_WITHOUT_ANCHOR);
        assertNull(excluded.resolvedAnchor());
        assertSame(item, excluded.original());
        assertEquals(1, result.omittedCount());
        assertTrue(result.reviewIssues().isEmpty());
    }

    @Test
    void excludesDisabledAreasAndPreventsMixedIssueKindsFromBypassingSettings() {
        AiCorrectionItem mixed = replace(
                new AiAnchor(1, "go", 1),
                AiArea.GRAMMAR_SPELLING,
                List.of(AiIssueKind.GRAMMAR, AiIssueKind.VOCABULARY),
                BOX);
        AiCorrectionItem disabled =
                replace(new AiAnchor(1, "go", 1), AiArea.VOCABULARY_EXPRESSION, List.of(AiIssueKind.VOCABULARY), BOX);
        AiValidationContext context =
                AiValidationContext.fromText(1, "go", Map.of(AiArea.GRAMMAR_SPELLING, AiCorrectionLevel.STANDARD));
        AiValidationResult result = validator.validate(new AiCorrectionResponse(List.of(mixed, disabled)), context);
        assertTrue(result.corrections().isEmpty());
        assertEquals(2, result.excludedItems().size());
        assertHas(result.candidates().get(0), AiValidationCode.ISSUE_AREA_NOT_SELECTED);
        assertHas(result.candidates().get(1), AiValidationCode.AREA_NOT_SELECTED);
        assertEquals(2, result.omittedCount());
        assertEquals(
                2,
                result.summary().stream().mapToInt(AiAreaSummary::omittedCount).sum());
    }

    @Test
    void requiresRepresentativeAreaToMatchAnIssueAndAllowsMultipleSelectedIssueAreas() {
        AiCorrectionItem mismatch =
                replace(new AiAnchor(1, "go", 1), AiArea.CONTENT_SUMMARY, List.of(AiIssueKind.GRAMMAR), BOX);
        assertHas(checkOne(mismatch, "go"), AiValidationCode.AREA_ISSUE_MISMATCH);
        assertEquals(AiItemDisposition.REVIEW, checkOne(mismatch, "go").disposition());
        AiCorrectionItem mixed = replace(
                new AiAnchor(1, "go", 1),
                AiArea.GRAMMAR_SPELLING,
                List.of(AiIssueKind.GRAMMAR, AiIssueKind.VOCABULARY),
                BOX);
        assertEquals(AiItemDisposition.CORRECTION, checkOne(mixed, "go").disposition());
    }

    @Test
    void capsPerPageAndAreaCountsReplaceAndGuidanceTogetherAndRetainsModelPriority() {
        List<AiCorrectionItem> items = new ArrayList<>();
        for (int line = 6; line >= 1; line--) items.add(card(line, AiArea.GRAMMAR_SPELLING, false));
        for (int line = 14; line >= 7; line--) items.add(card(line, AiArea.VOCABULARY_EXPRESSION, false));
        for (int line = 22; line >= 15; line--) items.add(card(line, AiArea.CONTENT_SUMMARY, line % 2 == 0));
        items.add(review());
        items.add(review());
        AiCorrectionResponse response = new AiCorrectionResponse(items);
        AiValidationContext context = new AiValidationContext(
                1, IntStream.range(0, 22).mapToObj(index -> "go").toList(), ALL_AREAS);
        AiValidationResult result = validator.validate(response, context);
        assertEquals(17, result.corrections().size());
        assertEquals(2, result.reviewIssues().size());
        assertEquals(5, result.omittedCount());
        assertEquals(5, summary(result, AiArea.GRAMMAR_SPELLING).displayedCount());
        assertEquals(7, summary(result, AiArea.VOCABULARY_EXPRESSION).displayedCount());
        assertEquals(5, summary(result, AiArea.CONTENT_SUMMARY).displayedCount());
        assertEquals(0, summary(result, AiArea.SENTENCE_STRUCTURE).displayedCount());
        assertEquals(3, summary(result, AiArea.GRAMMAR_SPELLING).recommendedCount());
        assertEquals(5, summary(result, AiArea.VOCABULARY_EXPRESSION).recommendedCount());
        assertEquals(AiItemDisposition.EXCLUDED, result.candidates().get(5).disposition());
        assertEquals(1, result.candidates().get(5).resolvedAnchor().lineId());
        assertEquals(2, result.corrections().getFirst().resolvedAnchor().lineId());
        result.excludedItems().forEach(item -> assertHas(item, AiValidationCode.CARD_LIMIT_EXCEEDED));
        AiValidationResult nextPage =
                validator.validate(response, new AiValidationContext(2, context.transcriptionLines(), ALL_AREAS));
        assertEquals(17, nextPage.corrections().size());
        assertTrue(nextPage.corrections().stream().allMatch(item -> item.pageNumber() == 2));
    }

    @Test
    void invalidCandidatesDoNotConsumeCardSlotsAndDeletionIsStillAReplaceCard() {
        List<AiCorrectionItem> items = new ArrayList<>();
        items.add(replace(new AiAnchor(1, "missing", 1), AiArea.GRAMMAR_SPELLING, List.of(AiIssueKind.GRAMMAR), BOX));
        for (int line = 1; line <= 6; line++) items.add(card(line, AiArea.GRAMMAR_SPELLING, false));
        AiCorrectionItem base = items.get(1);
        items.set(
                1,
                new AiCorrectionItem(
                        base.action(),
                        base.feedbackType(),
                        base.area(),
                        base.issueKinds(),
                        base.anchor(),
                        "",
                        null,
                        base.reason(),
                        false,
                        BOX));
        AiValidationResult result = validator.validate(
                new AiCorrectionResponse(items),
                new AiValidationContext(1, List.of("go", "go", "go", "go", "go", "go"), ALL_AREAS));
        assertEquals(5, result.corrections().size());
        assertEquals(1, result.reviewIssues().size());
        assertEquals(1, result.excludedItems().size());
        assertEquals("", result.corrections().getFirst().original().replacement());
    }

    @Test
    void marksOverlapOnBothVisibleCandidatesWithoutApplyingOrMergingThem() {
        AiCorrectionItem left =
                replace(new AiAnchor(1, "ab", 1), AiArea.GRAMMAR_SPELLING, List.of(AiIssueKind.GRAMMAR), BOX);
        AiCorrectionItem right =
                replace(new AiAnchor(1, "cd", 1), AiArea.GRAMMAR_SPELLING, List.of(AiIssueKind.GRAMMAR), BOX);
        AiValidationContext context = AiValidationContext.fromText(1, "abcd", ALL_AREAS);
        AiValidationResult adjacent = validator.validate(new AiCorrectionResponse(List.of(left, right)), context);
        assertTrue(adjacent.corrections().stream().noneMatch(AiValidatedItem::requiresReview));
        AiCorrectionItem both =
                replace(new AiAnchor(1, "abcd", 1), AiArea.GRAMMAR_SPELLING, List.of(AiIssueKind.GRAMMAR), BOX);
        AiValidationResult overlap = validator.validate(new AiCorrectionResponse(List.of(left, right, both)), context);
        assertEquals(3, overlap.corrections().size());
        overlap.corrections().forEach(item -> assertHas(item, AiValidationCode.OVERLAPPING_ANCHOR));
        assertEquals("abcd", overlap.context().transcriptionLines().getFirst());
    }

    @Test
    void excludedCandidatesDoNotAddOverlapWarningsToVisibleCards() {
        AiCorrectionItem visible = card(1, AiArea.GRAMMAR_SPELLING, false);
        AiCorrectionItem excluded = card(1, AiArea.VOCABULARY_EXPRESSION, false);
        AiValidationResult result = validator.validate(
                new AiCorrectionResponse(List.of(visible, excluded)),
                AiValidationContext.fromText(1, "go", Map.of(AiArea.GRAMMAR_SPELLING, AiCorrectionLevel.STANDARD)));
        assertFalse(result.corrections().getFirst().requiresReview());
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "Add one reason.",
                "Don't forget one well-chosen example.",
                "One two three four five six seven eight nine ten."
            })
    void acceptsEnglishGuidanceThroughTenWordsIncludingContractionsAndHyphens(String guidance) {
        AiCorrectionItem item = new AiCorrectionItem(
                AiAction.GUIDANCE,
                AiFeedbackType.SUGGESTION,
                AiArea.CONTENT_SUMMARY,
                List.of(AiIssueKind.CONTENT),
                new AiAnchor(1, "go", 1),
                null,
                guidance,
                "선택적인 개선 제안입니다.",
                false,
                BOX);
        assertEquals(AiItemDisposition.CORRECTION, checkOne(item, "go").disposition());
    }

    @Test
    void emptyItemsAreSuccessfulOnlyWithReadableTranscriptionAndNothingIsPadded() {
        AiValidationResult result = validator.validate(
                new AiCorrectionResponse(List.of()), AiValidationContext.fromText(1, "go", ALL_AREAS));
        assertTrue(result.candidates().isEmpty());
        assertEquals(0, result.omittedCount());
        assertTrue(result.summary().stream().allMatch(area -> area.displayedCount() == 0));
        assertThrows(IllegalArgumentException.class, () -> AiValidationContext.fromText(1, " \r\n\r\n", ALL_AREAS));
        assertThrows(IllegalArgumentException.class, () -> new AiValidationContext(1, List.of("go\nnow"), ALL_AREAS));
        assertThrows(IllegalArgumentException.class, () -> AiValidationContext.fromText(0, "go", ALL_AREAS));
    }

    private AiValidatedItem checkOne(AiCorrectionItem item, String text) {
        return validator
                .validate(new AiCorrectionResponse(List.of(item)), AiValidationContext.fromText(1, text, ALL_AREAS))
                .candidates()
                .getFirst();
    }

    private static AiCorrectionItem replace(
            AiAnchor anchor, AiArea area, List<AiIssueKind> kinds, List<List<Double>> boxes) {
        return new AiCorrectionItem(
                AiAction.REPLACE,
                AiFeedbackType.CORRECTION,
                area,
                kinds,
                anchor,
                "went",
                null,
                "고정 데이터의 수정 제안입니다.",
                false,
                boxes);
    }

    private static AiCorrectionItem card(int line, AiArea area, boolean guidance) {
        AiIssueKind kind =
                switch (area) {
                    case GRAMMAR_SPELLING -> AiIssueKind.GRAMMAR;
                    case VOCABULARY_EXPRESSION -> AiIssueKind.VOCABULARY;
                    case SENTENCE_STRUCTURE -> AiIssueKind.SENTENCE_STRUCTURE;
                    case CONTENT_SUMMARY -> AiIssueKind.CONTENT;
                };
        if (!guidance) return replace(new AiAnchor(line, "go", 1), area, List.of(kind), BOX);
        return new AiCorrectionItem(
                AiAction.GUIDANCE,
                AiFeedbackType.SUGGESTION,
                area,
                List.of(kind),
                new AiAnchor(line, "go", 1),
                null,
                "Add one example.",
                "선택적인 보완 제안입니다.",
                false,
                BOX);
    }

    private static AiCorrectionItem review() {
        return new AiCorrectionItem(
                AiAction.REVIEW,
                null,
                null,
                List.of(AiIssueKind.IMAGE_PROBLEM),
                null,
                null,
                null,
                "사진 상태를 확인해 주세요.",
                true,
                List.of());
    }

    private static AiAreaSummary summary(AiValidationResult result, AiArea area) {
        return result.summary().stream()
                .filter(value -> value.area() == area)
                .findFirst()
                .orElseThrow();
    }

    private static void assertHas(AiValidatedItem item, AiValidationCode code) {
        assertTrue(item.issues().stream().anyMatch(issue -> issue.code() == code), () -> code + ": " + item.issues());
    }

    private static String fixture(String name) throws IOException {
        try (var stream = AiCorrectionResponseValidatorTest.class.getResourceAsStream("/ai/contract/v1/" + name)) {
            assertNotNull(stream);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
