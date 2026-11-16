package com.ceos24.autedi.domain.generation.contract.v1_1;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
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

/** 실제 v1.1 파서와 합성 자료만 사용한다. Spring·DB·외부 API·v1 생성 경로를 실행하지 않는다. */
class AiCorrectionResponseParserTest {
    private static final String ROOT = "/ai/contract/v1.1/";
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private final AiCorrectionResponseParser parser = new AiCorrectionResponseParser();

    @ParameterizedTest(name = "{0}")
    @MethodSource("fixedCases")
    void matchesEveryFixtureParsingExpectation(String id, String responseFile, String expected) throws IOException {
        String raw = resource(responseFile);
        if (expected.equals("REJECT")) {
            assertInvalid(raw, null);
        } else {
            assertEquals("ACCEPT", expected, id);
            AiCorrectionResponse result = parser.parse(raw);
            assertEquals(JSON.readTree(raw), JSON.valueToTree(result), id);
        }
    }

    static Stream<Arguments> fixedCases() throws IOException {
        List<Arguments> arguments = new ArrayList<>();
        for (JsonNode fixture : JSON.readTree(resource("cases.json")).get("cases")) {
            arguments.add(Arguments.of(
                    fixture.get("id").asString(),
                    fixture.get("response_file").asString(),
                    fixture.at("/expected/parse_result").asString()));
        }
        return arguments.stream();
    }

    @Test
    void readsTypedInsertionAndPreservesDeletionNullsAndMultipleAnchors() throws IOException {
        AiCorrectionItem insertion = parser.parse(resource("responses/standard-insert-before.json"))
                .items()
                .getFirst();
        assertEquals(List.of(new AiAnchor(1, "dog", 1)), insertion.anchors());
        assertEquals("a dog", insertion.replacement());
        assertEquals(AiRevisionScope.WORD, insertion.revisionScope());
        assertEquals(new AiEdit(AiEditOperation.INSERT, "a", AiEditPosition.BEFORE), insertion.edit());

        AiCorrectionItem deletion = parser.parse(resource("responses/standard-delete-word.json"))
                .items()
                .getFirst();
        assertEquals("", deletion.replacement());
        assertEquals(new AiEdit(AiEditOperation.DELETE, "", null), deletion.edit());

        AiCorrectionItem review = parser.parse(resource("responses/review-image-problem.json"))
                .items()
                .getFirst();
        assertTrue(review.anchors().isEmpty());
        assertNull(review.edit());
        assertNull(review.revisionScope());

        AiCorrectionItem structure = parser.parse(resource("responses/advanced-structure-multiline.json"))
                .items()
                .getFirst();
        assertEquals(3, structure.anchors().size());
        assertEquals(AiRevisionScope.SENTENCE_GROUP, structure.revisionScope());
        assertEquals(AiEditOperation.RESTRUCTURE, structure.edit().operation());
    }

    @Test
    void acceptsAnEmptyResponseWithSurroundingWhitespace() {
        assertEquals(new AiCorrectionResponse(List.of()), parser.parse(" \r\n{\"items\":[]}\t\n"));
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
                "true",
                "{}",
                "{\"items\":null}",
                "{\"items\":{}}",
                "{\"items\":[null]}",
                "{\"items\":[[]]}",
                "{\"items\":[]} {}",
                "{\"items\":[]} true",
                "{\"edits\":[]}",
                "{\"result\":{\"items\":[]}}",
                "```json\n{\"items\":[]}\n```",
                "{\"items\":[",
                "{\"items\":[],}",
                "{'items':[]}",
                "// comment\n{\"items\":[]}"
            })
    void rejectsMissingMalformedWrappedOrMultipleResponses(String raw) {
        assertInvalid(raw, null);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "action",
                "feedback_type",
                "area",
                "issue_kinds",
                "anchors",
                "replacement",
                "guidance",
                "reason",
                "location_uncertain",
                "target_boxes",
                "revision_scope",
                "edit"
            })
    void requiresEveryItemKeyEvenIfItsValueCanBeNull(String field) throws IOException {
        ObjectNode root = example();
        first(root).remove(field);
        assertInvalid(root.toString(), "$.items[0]");
    }

    @ParameterizedTest
    @ValueSource(strings = {"line_id", "original", "occurrence"})
    void requiresEveryAnchorKey(String field) throws IOException {
        ObjectNode root = example();
        ((ObjectNode) first(root).at("/anchors/0")).remove(field);
        assertInvalid(root.toString(), "$.items[0].anchors[0]");
    }

    @ParameterizedTest
    @ValueSource(strings = {"operation", "text", "position"})
    void requiresEveryEditKeyIncludingNullablePosition(String field) throws IOException {
        ObjectNode root = example();
        ((ObjectNode) first(root).get("edit")).remove(field);
        assertInvalid(root.toString(), "$.items[0].edit");
    }

    @ParameterizedTest
    @ValueSource(strings = {"response", "item", "anchor", "edit"})
    void rejectsAdditionalKeysAtEveryObjectLevel(String level) throws IOException {
        ObjectNode root = example();
        ObjectNode object =
                switch (level) {
                    case "response" -> root;
                    case "item" -> first(root);
                    case "anchor" -> (ObjectNode) first(root).at("/anchors/0");
                    default -> (ObjectNode) first(root).get("edit");
                };
        object.put("unexpected", "value");
        assertInvalid(root.toString(), null);
    }

    @ParameterizedTest
    @ValueSource(strings = {"response", "item", "anchor", "edit"})
    void rejectsDuplicateKeysIncludingNestedObjects(String level) throws IOException {
        String raw = example().toString();
        String duplicated =
                switch (level) {
                    case "response" -> "{\"items\":[],\"items\":[]}";
                    case "item" -> raw.replace("\"action\":", "\"action\":\"REPLACE\",\"action\":");
                    case "anchor" -> raw.replace("\"line_id\":", "\"line_id\":1,\"line_id\":");
                    default -> raw.replace("\"position\":", "\"position\":\"BEFORE\",\"position\":");
                };
        assertNotEquals(raw, duplicated);
        assertInvalid(duplicated, null);
    }

    @ParameterizedTest
    @MethodSource("invalidItemFields")
    void rejectsWrongTypesUnknownEnumsAndMalformedArrays(String field, String value) throws IOException {
        ObjectNode root = example();
        first(root).set(field, JSON.readTree(value));
        assertInvalid(root.toString(), "$.items[0]." + field);
    }

    static Stream<Arguments> invalidItemFields() {
        return Stream.of(
                Arguments.of("action", "null"),
                Arguments.of("action", "0"),
                Arguments.of("action", "\"replace\""),
                Arguments.of("action", "\"UNKNOWN\""),
                Arguments.of("feedback_type", "true"),
                Arguments.of("feedback_type", "\"REVIEW\""),
                Arguments.of("area", "0"),
                Arguments.of("area", "\"GRAMMAR_MECHANICS\""),
                Arguments.of("issue_kinds", "null"),
                Arguments.of("issue_kinds", "[]"),
                Arguments.of("issue_kinds", "{}"),
                Arguments.of("issue_kinds", "[null]"),
                Arguments.of("issue_kinds", "[0]"),
                Arguments.of("issue_kinds", "[\"grammar\",\"grammar\"]"),
                Arguments.of("issue_kinds", "[\"GRAMMAR\"]"),
                Arguments.of("issue_kinds", "[\"needs_review\"]"),
                Arguments.of("anchors", "null"),
                Arguments.of("anchors", "{}"),
                Arguments.of("anchors", "[null]"),
                Arguments.of("anchors", "[[]]"),
                Arguments.of("replacement", "12"),
                Arguments.of("guidance", "false"),
                Arguments.of("reason", "null"),
                Arguments.of("reason", "\"\""),
                Arguments.of("reason", "12"),
                Arguments.of("location_uncertain", "null"),
                Arguments.of("location_uncertain", "\"false\""),
                Arguments.of("location_uncertain", "0"),
                Arguments.of("target_boxes", "null"),
                Arguments.of("target_boxes", "[null]"),
                Arguments.of("target_boxes", "[[0,0,1]]"),
                Arguments.of("target_boxes", "[[0,0,1,1,1]]"),
                Arguments.of("target_boxes", "[[\"0\",0,1,1]]"),
                Arguments.of("target_boxes", "[[true,0,1,1]]"),
                Arguments.of("target_boxes", "[[null,0,1,1]]"),
                Arguments.of("target_boxes", "[[-0.1,0,1,1]]"),
                Arguments.of("target_boxes", "[[0,0,1.1,1]]"),
                Arguments.of("revision_scope", "0"),
                Arguments.of("revision_scope", "\"PARAGRAPH\""),
                Arguments.of("revision_scope", "\"word\""),
                Arguments.of("edit", "[]"),
                Arguments.of("edit", "\"INSERT\""));
    }

    @ParameterizedTest
    @MethodSource("invalidAnchorFields")
    void rejectsInvalidAnchorNumbersAndMultilineOriginals(String field, String value) throws IOException {
        ObjectNode root = example();
        ((ObjectNode) first(root).at("/anchors/0")).set(field, JSON.readTree(value));
        assertInvalid(root.toString(), "$.items[0].anchors[0]." + field);
    }

    static Stream<Arguments> invalidAnchorFields() {
        return Stream.of(
                Arguments.of("line_id", "0"),
                Arguments.of("line_id", "-1"),
                Arguments.of("line_id", "1.5"),
                Arguments.of("line_id", "1.0"),
                Arguments.of("line_id", "\"1\""),
                Arguments.of("line_id", "null"),
                Arguments.of("line_id", "2147483648"),
                Arguments.of("occurrence", "0"),
                Arguments.of("occurrence", "1.5"),
                Arguments.of("occurrence", "\"1\""),
                Arguments.of("occurrence", "null"),
                Arguments.of("occurrence", "2147483648"),
                Arguments.of("original", "null"),
                Arguments.of("original", "12"),
                Arguments.of("original", "\"\""),
                Arguments.of("original", "\"go\\nnow\""),
                Arguments.of("original", "\"go\\rnow\""),
                Arguments.of("original", "\"go\\n\""));
    }

    @ParameterizedTest
    @MethodSource("invalidEditFields")
    void rejectsWrongEditTypesAndUnknownOperationsOrPositions(String field, String value) throws IOException {
        ObjectNode root = example();
        ((ObjectNode) first(root).get("edit")).set(field, JSON.readTree(value));
        assertInvalid(root.toString(), "$.items[0].edit." + field);
    }

    static Stream<Arguments> invalidEditFields() {
        return Stream.of(
                Arguments.of("operation", "null"),
                Arguments.of("operation", "0"),
                Arguments.of("operation", "\"insert\""),
                Arguments.of("operation", "\"MOVE\""),
                Arguments.of("text", "null"),
                Arguments.of("text", "12"),
                Arguments.of("position", "0"),
                Arguments.of("position", "\"before\""),
                Arguments.of("position", "\"INSIDE\""));
    }

    @Test
    void rejectsNumericOverflowInCoordinates() throws IOException {
        String raw = example().toString();
        raw = raw.replace("\"target_boxes\":[", "\"target_boxes\":[[1e309,0,1,1],");
        assertInvalid(raw, "$.items[0].target_boxes[0][0]");
    }

    @Test
    void retainsShapeValidCandidatesForPostProcessingWithoutFixingThem() throws IOException {
        ObjectNode root = example();
        ObjectNode item = first(root);
        item.set("anchors", JSON.readTree("[]"));
        item.put("guidance", "This belongs to another action.");
        item.put("revision_scope", "SENTENCE");
        item.set("target_boxes", JSON.readTree("[[0.8,0.5,0.2,0.5],[0.0,0.0,1.0,1.0]]"));
        ((ObjectNode) item.get("edit")).put("text", "went");

        AiCorrectionResponse response = parser.parse(root.toString());
        assertEquals(JSON.readTree(root.toString()), JSON.valueToTree(response));
        assertTrue(response.items().getFirst().anchors().isEmpty());
        assertEquals(
                List.of(0.8, 0.5, 0.2, 0.5),
                response.items().getFirst().targetBoxes().getFirst());
    }

    @Test
    void preservesWhitespaceAndMaximumIntAnchorValues() throws IOException {
        ObjectNode root = example();
        ObjectNode anchor = (ObjectNode) first(root).at("/anchors/0");
        anchor.put("line_id", Integer.MAX_VALUE);
        anchor.put("occurrence", Integer.MAX_VALUE);
        anchor.put("original", " dog ");
        first(root).put("replacement", " a dog ");
        AiCorrectionItem result = parser.parse(root.toString()).items().getFirst();
        assertEquals(
                new AiAnchor(Integer.MAX_VALUE, " dog ", Integer.MAX_VALUE),
                result.anchors().getFirst());
        assertEquals(" a dog ", result.replacement());
    }

    private void assertInvalid(String raw, String path) {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> parser.parse(raw));
        assertTrue(error.getMessage().startsWith(AiCorrectionContract.CONTRACT_ID), error.getMessage());
        if (path != null) assertTrue(error.getMessage().contains(path), error.getMessage());
    }

    private static ObjectNode example() throws IOException {
        return (ObjectNode) JSON.readTree(resource("responses/standard-insert-before.json"));
    }

    private static ObjectNode first(ObjectNode root) {
        return (ObjectNode) root.get("items").get(0);
    }

    private static String resource(String relative) throws IOException {
        try (var input = AiCorrectionResponseParserTest.class.getResourceAsStream(ROOT + relative)) {
            assertNotNull(input, relative);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
