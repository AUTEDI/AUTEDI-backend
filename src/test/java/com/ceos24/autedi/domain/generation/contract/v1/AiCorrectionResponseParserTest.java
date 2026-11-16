package com.ceos24.autedi.domain.generation.contract.v1;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/** 고정 JSON만 사용한다. Spring context, DB, 외부 API 및 실험 main을 실행하지 않는다. */
class AiCorrectionResponseParserTest {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private final AiCorrectionResponseParser parser = new AiCorrectionResponseParser();

    @Test
    void readsAllThreeActionsFromContractExample() throws IOException {
        AiCorrectionResponse response = parser.parse(fixture());
        assertEquals(3, response.items().size());

        AiCorrectionItem replace = response.items().get(0);
        assertEquals(AiAction.REPLACE, replace.action());
        assertEquals(AiFeedbackType.CORRECTION, replace.feedbackType());
        assertEquals(AiArea.GRAMMAR_SPELLING, replace.area());
        assertEquals(List.of(AiIssueKind.GRAMMAR), replace.issueKinds());
        assertEquals(new AiAnchor(1, "go", 1), replace.anchor());
        assertEquals("went", replace.replacement());
        assertNull(replace.guidance());
        assertEquals("과거 시점인 yesterday에 맞춰 과거형 went를 사용합니다.", replace.reason());
        assertFalse(replace.locationUncertain());
        assertEquals(List.of(List.of(0.30, 0.37, 0.34, 0.42)), replace.boxes());

        AiCorrectionItem guidance = response.items().get(1);
        assertEquals(AiAction.GUIDANCE, guidance.action());
        assertEquals(AiFeedbackType.SUGGESTION, guidance.feedbackType());
        assertEquals(AiArea.CONTENT_SUMMARY, guidance.area());
        assertEquals(List.of(AiIssueKind.CONTENT), guidance.issueKinds());
        assertEquals(new AiAnchor(2, "I like this book.", 1), guidance.anchor());
        assertNull(guidance.replacement());
        assertEquals("Add one reason you like this book.", guidance.guidance());

        AiCorrectionItem review = response.items().get(2);
        assertEquals(AiAction.REVIEW, review.action());
        assertNull(review.feedbackType());
        assertNull(review.area());
        assertNull(review.anchor());
        assertNull(review.replacement());
        assertNull(review.guidance());
        assertEquals(List.of(AiIssueKind.IMAGE_PROBLEM), review.issueKinds());
        assertTrue(review.locationUncertain());
        assertTrue(review.boxes().isEmpty());
    }

    @Test
    void acceptsEmptyItemsAsSuccessfulResponse() {
        assertEquals(new AiCorrectionResponse(List.of()), parser.parse("{\"items\":[]}"));
    }

    @Test
    void distinguishesDeletionFromNullReplacement() throws IOException {
        ObjectNode root = example();
        firstItem(root).put("replacement", "");
        AiCorrectionResponse response = parser.parse(root.toString());
        assertEquals("", response.items().getFirst().replacement());
        assertNull(response.items().get(1).replacement());
    }

    @Test
    void readsReviewWithAnchorAndMultipleIssueKinds() throws IOException {
        ObjectNode root = example();
        ObjectNode review = (ObjectNode) root.get("items").get(2);
        review.set("anchor", firstItem(root).get("anchor"));
        review.set("issue_kinds", JSON.readTree("[\"ocr_mismatch\",\"illegible\"]"));
        review.set("boxes", JSON.readTree("[[0,0,1,1],[0.1,0.2,0.3,0.4]]"));

        AiCorrectionItem result = parser.parse(root.toString()).items().get(2);
        assertEquals(new AiAnchor(1, "go", 1), result.anchor());
        assertEquals(List.of(AiIssueKind.OCR_MISMATCH, AiIssueKind.ILLEGIBLE), result.issueKinds());
        assertEquals(List.of(0.0, 0.0, 1.0, 1.0), result.boxes().getFirst());
        assertEquals(2, result.boxes().size());
    }

    @Test
    void serializesSnakeCaseEnumsAndExplicitNullsWithoutChangingPayload() throws IOException {
        JsonNode expected = JSON.readTree(fixture());
        JsonNode actual = JSON.readTree(JSON.writeValueAsString(parser.parse(fixture())));
        assertEquals(expected, actual);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(
            strings = {
                "",
                " ",
                "null",
                "[]",
                "1",
                "{}",
                "{\"items\":null}",
                "{\"items\":{}}",
                "{\"edits\":[]}",
                "{\"items\":[null]}",
                "{\"items\":[]} {}",
                "{\"items\":[],\"items\":[]}",
                "```json\n{\"items\":[]}\n```",
                "{\"items\":["
            })
    void rejectsUnreadableResponsesAndLegacyFormat(String json) {
        assertThrows(IllegalArgumentException.class, () -> parser.parse(json));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "action",
                "feedback_type",
                "area",
                "issue_kinds",
                "anchor",
                "replacement",
                "guidance",
                "reason",
                "location_uncertain",
                "boxes"
            })
    void requiresEveryItemFieldEvenWhenItsValueCanBeNull(String field) throws IOException {
        ObjectNode root = example();
        firstItem(root).remove(field);
        assertThrows(IllegalArgumentException.class, () -> parser.parse(root.toString()));
    }

    @Test
    void rejectsAdditionalFieldsAtEveryObjectLevel() throws IOException {
        for (String level : List.of("response", "item", "anchor")) {
            ObjectNode root = example();
            ObjectNode target =
                    switch (level) {
                        case "response" -> root;
                        case "item" -> firstItem(root);
                        default -> (ObjectNode) firstItem(root).get("anchor");
                    };
            target.put("unexpected", "value");
            assertThrows(IllegalArgumentException.class, () -> parser.parse(root.toString()), level);
        }
    }

    @ParameterizedTest
    @MethodSource("invalidFields")
    void rejectsUnknownEnumsWrongTypesAndInvalidArrayShapes(String field, String value) throws IOException {
        ObjectNode root = example();
        firstItem(root).set(field, JSON.readTree(value));
        assertThrows(IllegalArgumentException.class, () -> parser.parse(root.toString()));
    }

    static Stream<Arguments> invalidFields() {
        return Stream.of(
                Arguments.of("action", "\"replace\""),
                Arguments.of("action", "\"UNKNOWN\""),
                Arguments.of("action", "0"),
                Arguments.of("action", "null"),
                Arguments.of("feedback_type", "\"REVIEW\""),
                Arguments.of("area", "\"GRAMMAR_MECHANICS\""),
                Arguments.of("issue_kinds", "[\"needs_review\"]"),
                Arguments.of("issue_kinds", "[\"GRAMMAR\"]"),
                Arguments.of("issue_kinds", "[\"grammar\",\"grammar\"]"),
                Arguments.of("issue_kinds", "[]"),
                Arguments.of("issue_kinds", "[null]"),
                Arguments.of("replacement", "12"),
                Arguments.of("guidance", "false"),
                Arguments.of("reason", "null"),
                Arguments.of("reason", "\"\""),
                Arguments.of("location_uncertain", "\"false\""),
                Arguments.of("location_uncertain", "null"),
                Arguments.of("boxes", "null"),
                Arguments.of("boxes", "[null]"),
                Arguments.of("boxes", "[[0,0,1]]"),
                Arguments.of("boxes", "[[0,0,1,0,1,1,0,1]]"),
                Arguments.of("boxes", "[[\"0\",0,1,1]]"),
                Arguments.of("boxes", "[[-0.1,0,1,1]]"),
                Arguments.of("boxes", "[[0,0,1.1,1]]"));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{\"original\":\"go\",\"occurrence\":1}",
                "{\"line_id\":0,\"original\":\"go\",\"occurrence\":1}",
                "{\"line_id\":1.5,\"original\":\"go\",\"occurrence\":1}",
                "{\"line_id\":\"1\",\"original\":\"go\",\"occurrence\":1}",
                "{\"line_id\":1,\"original\":\"go\",\"occurrence\":0}",
                "{\"line_id\":1,\"original\":\"\",\"occurrence\":1}",
                "{\"line_id\":1,\"original\":\"go\\nnow\",\"occurrence\":1}"
            })
    void rejectsMalformedAnchors(String anchor) throws IOException {
        ObjectNode root = example();
        firstItem(root).set("anchor", JSON.readTree(anchor));
        assertThrows(IllegalArgumentException.class, () -> parser.parse(root.toString()));
    }

    @Test
    void retainsSemanticallyUnvalidatedCandidatesForLaterPostProcessing() throws IOException {
        ObjectNode root = example();
        // 읽을 수 있는 구조여도 REPLACE의 anchor 누락·면적 0·guidance 혼재는 후처리 대상이다.
        firstItem(root).putNull("anchor");
        firstItem(root).put("guidance", "This belongs to another action.");
        firstItem(root).set("boxes", JSON.readTree("[[0.5,0.5,0.5,0.5]]"));

        AiCorrectionItem candidate = parser.parse(root.toString()).items().getFirst();
        assertEquals(AiAction.REPLACE, candidate.action());
        assertNull(candidate.anchor());
        assertEquals("went", candidate.replacement());
        assertEquals("This belongs to another action.", candidate.guidance());
        assertEquals(List.of(List.of(0.5, 0.5, 0.5, 0.5)), candidate.boxes());
    }

    private static ObjectNode example() throws IOException {
        return (ObjectNode) JSON.readTree(fixture());
    }

    private static ObjectNode firstItem(ObjectNode root) {
        return (ObjectNode) root.get("items").get(0);
    }

    private static String fixture() throws IOException {
        try (var stream =
                AiCorrectionResponseParserTest.class.getResourceAsStream("/ai/contract/v1/correction-response.json")) {
            assertNotNull(stream);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
