package com.ceos24.autedi.domain.generation.contract.v1_1;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 일반 Jackson 매핑과 자료 보존만 검사한다. 엄격한 v1.1 파서와 문맥 검증기는 후속 작업이다. */
class AiCorrectionResponseDtoTest {
    private static final String ROOT = "/ai/contract/v1.1/";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Test
    void roundTripsEveryStructurallyReadableFixtureWithoutNormalizingTheCandidate() throws IOException {
        JsonNode cases = JSON.readTree(resource("cases.json")).get("cases");
        int checked = 0;
        for (JsonNode fixture : cases) {
            if (!fixture.at("/expected/parse_result").asString().equals("ACCEPT")) continue;
            String raw = resource(fixture.get("response_file").asString());
            AiCorrectionResponse response = JSON.readValue(raw, AiCorrectionResponse.class);
            assertEquals(
                    JSON.readTree(raw),
                    JSON.valueToTree(response),
                    fixture.get("id").asString());
            checked++;
        }
        assertTrue(checked > 0);
    }

    @Test
    void readsTheInsertionOperationSeparatelyFromItsReplacement() throws IOException {
        AiCorrectionItem item = fixture("standard-insert-before").items().getFirst();
        assertEquals(AiAction.REPLACE, item.action());
        assertEquals(AiFeedbackType.CORRECTION, item.feedbackType());
        assertEquals(AiArea.GRAMMAR_SPELLING, item.area());
        assertEquals(List.of(AiIssueKind.GRAMMAR), item.issueKinds());
        assertEquals(List.of(new AiAnchor(1, "dog", 1)), item.anchors());
        assertEquals("a dog", item.replacement());
        assertNull(item.guidance());
        assertEquals(AiRevisionScope.WORD, item.revisionScope());
        assertEquals(new AiEdit(AiEditOperation.INSERT, "a", AiEditPosition.BEFORE), item.edit());
        assertEquals(List.of(List.of(0.30, 0.20, 0.36, 0.23)), item.targetBoxes());

        AiCorrectionItem punctuation = fixture("standard-insert-after").items().getFirst();
        assertEquals(new AiEdit(AiEditOperation.INSERT, ".", AiEditPosition.AFTER), punctuation.edit());
        assertEquals(List.of(AiIssueKind.PUNCTUATION), punctuation.issueKinds());
    }

    @Test
    void preservesEmptyDeletionTextAndNullFieldsForGuidanceAndReview() throws IOException {
        AiCorrectionItem deletion = fixture("standard-delete-word").items().getFirst();
        assertEquals("", deletion.replacement());
        assertEquals(new AiEdit(AiEditOperation.DELETE, "", null), deletion.edit());
        JsonNode deletionJson = JSON.valueToTree(deletion);
        assertTrue(deletionJson.at("/edit/position").isNull());
        assertTrue(deletionJson.get("guidance").isNull());
        assertEquals("", deletionJson.at("/edit/text").asString());

        AiCorrectionItem guidance = fixture("advanced-content-guidance").items().getFirst();
        assertEquals(AiAction.GUIDANCE, guidance.action());
        assertNull(guidance.replacement());
        assertEquals("Explain how volunteering benefits students.", guidance.guidance());
        assertNull(guidance.revisionScope());
        assertNull(guidance.edit());

        AiCorrectionItem review = fixture("review-image-problem").items().getFirst();
        assertEquals(AiAction.REVIEW, review.action());
        assertNull(review.feedbackType());
        assertNull(review.area());
        assertTrue(review.anchors().isEmpty());
        assertTrue(review.targetBoxes().isEmpty());
        assertTrue(review.locationUncertain());
        JsonNode reviewJson = JSON.valueToTree(review);
        for (String field : List.of("feedback_type", "area", "replacement", "guidance", "revision_scope", "edit")) {
            assertTrue(reviewJson.has(field), field);
            assertTrue(reviewJson.get(field).isNull(), field);
        }
    }

    @Test
    void preservesMultipleAnchorsAndDoesNotInferScopeFromTheNumberOfLines() throws IOException {
        AiCorrectionItem structure =
                fixture("advanced-structure-multiline").items().getFirst();
        assertEquals(3, structure.anchors().size());
        assertEquals(new AiAnchor(3, "I went to bed.", 1), structure.anchors().getLast());
        assertEquals(AiRevisionScope.SENTENCE_GROUP, structure.revisionScope());
        assertEquals(AiEditOperation.RESTRUCTURE, structure.edit().operation());
        assertEquals(structure.replacement(), structure.edit().text());
        assertNull(structure.edit().position());

        AiCorrectionItem phrase = fixture("grammar-phrase-across-lines").items().getFirst();
        assertEquals(2, phrase.anchors().size());
        assertEquals(AiRevisionScope.PHRASE, phrase.revisionScope());
        assertEquals(AiArea.GRAMMAR_SPELLING, phrase.area());
    }

    @Test
    void defensivelyCopiesAllListsIncludingNestedCoordinates() {
        List<AiIssueKind> kinds = new ArrayList<>(List.of(AiIssueKind.GRAMMAR));
        List<AiAnchor> anchors = new ArrayList<>(List.of(new AiAnchor(1, "dog", 1)));
        List<Double> box = new ArrayList<>(List.of(0.30, 0.20, 0.36, 0.23));
        List<List<Double>> boxes = new ArrayList<>();
        boxes.add(box);
        AiCorrectionItem item = new AiCorrectionItem(
                AiAction.REPLACE,
                AiFeedbackType.CORRECTION,
                AiArea.GRAMMAR_SPELLING,
                kinds,
                anchors,
                "a dog",
                null,
                "관사 a를 추가합니다.",
                false,
                boxes,
                AiRevisionScope.WORD,
                new AiEdit(AiEditOperation.INSERT, "a", AiEditPosition.BEFORE));
        List<AiCorrectionItem> items = new ArrayList<>(List.of(item));
        AiCorrectionResponse response = new AiCorrectionResponse(items);
        items.clear();
        kinds.clear();
        anchors.clear();
        box.set(0, 0.0);
        boxes.clear();

        assertEquals(List.of(item), response.items());
        assertEquals(List.of(AiIssueKind.GRAMMAR), item.issueKinds());
        assertEquals(List.of(new AiAnchor(1, "dog", 1)), item.anchors());
        assertEquals(List.of(List.of(0.30, 0.20, 0.36, 0.23)), item.targetBoxes());
        assertThrows(UnsupportedOperationException.class, () -> response.items().clear());
        assertThrows(
                UnsupportedOperationException.class, () -> item.issueKinds().clear());
        assertThrows(UnsupportedOperationException.class, () -> item.anchors().clear());
        assertThrows(
                UnsupportedOperationException.class, () -> item.targetBoxes().clear());
        assertThrows(
                UnsupportedOperationException.class,
                () -> item.targetBoxes().getFirst().set(0, 0.0));
    }

    @Test
    void supportsEmptyItemsWithoutInventingACorrection() {
        AiCorrectionResponse response = JSON.readValue("{\"items\":[]}", AiCorrectionResponse.class);
        assertTrue(response.items().isEmpty());
        assertEquals(JSON.readTree("{\"items\":[]}"), JSON.valueToTree(response));
    }

    private static AiCorrectionResponse fixture(String name) throws IOException {
        return JSON.readValue(resource("responses/" + name + ".json"), AiCorrectionResponse.class);
    }

    private static String resource(String relative) throws IOException {
        try (var input = AiCorrectionResponseDtoTest.class.getResourceAsStream(ROOT + relative)) {
            assertNotNull(input, relative);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
