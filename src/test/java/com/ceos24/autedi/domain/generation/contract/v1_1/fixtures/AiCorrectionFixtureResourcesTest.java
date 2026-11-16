package com.ceos24.autedi.domain.generation.contract.v1_1.fixtures;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 고정 자료의 참조·기대 결과 무결성만 검사한다. v1.1 파서·검증기나 실제 AI를 구현·호출하지 않는다. */
class AiCorrectionFixtureResourcesTest {
    private static final String ROOT = "/ai/contract/v1.1/";
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Set<String> AREAS =
            Set.of("GRAMMAR_SPELLING", "VOCABULARY_EXPRESSION", "SENTENCE_STRUCTURE", "CONTENT_SUMMARY");

    @Test
    void manifestDeclaresUniqueCasesWithSelfContainedInputContexts() throws IOException {
        JsonNode manifest = JSON.readTree(resource("cases.json"));
        assertEquals(1, manifest.get("fixture_format_version").intValue());
        assertEquals("AUTEDI-AI-CONTRACT-v1.1", manifest.get("contract_version").asString());
        assertEquals("SYNTHETIC", manifest.get("source").asString());
        Set<String> ids = new HashSet<>();
        Set<String> responses = new HashSet<>();
        for (JsonNode fixture : cases()) {
            String id = fixture.get("id").asString();
            assertTrue(ids.add(id), id);
            assertTrue(id.matches("[a-z0-9-]+"), id);
            assertFalse(fixture.get("description").asString().isBlank(), id);
            assertTrue(fixture.get("tags").isArray(), id);
            assertFalse(fixture.get("tags").isEmpty(), id);
            assertTrue(
                    Set.of("valid", "edge", "validation", "parser")
                            .contains(fixture.get("category").asString()),
                    id);
            String response = fixture.get("response_file").asString();
            assertTrue(response.matches("responses/[a-z0-9-]+\\.(json|txt)"), id);
            assertTrue(responses.add(response), id);
            assertFalse(resource(response).isBlank(), id);

            JsonNode context = fixture.get("context");
            assertTrue(context.get("page_number").intValue() > 0, id);
            int lineId = 1;
            for (JsonNode line : context.get("transcription_lines")) {
                assertEquals(lineId++, line.get("line_id").intValue(), id);
                assertTrue(line.get("text").isString(), id);
            }
            Set<String> selected = new HashSet<>();
            for (JsonNode setting : context.get("selected_areas")) {
                String area = setting.get("area").asString();
                assertTrue(AREAS.contains(area), id);
                assertTrue(selected.add(area), id);
                assertTrue(
                        Set.of("STANDARD", "ADVANCED")
                                .contains(setting.get("level").asString()),
                        id);
            }
            assertFalse(selected.isEmpty(), id);
        }
    }

    @Test
    void responseDocumentsAndExpectedClassificationsReferenceEveryCandidateOnce() throws IOException {
        for (JsonNode fixture : cases()) {
            String id = fixture.get("id").asString();
            JsonNode expected = fixture.get("expected");
            String raw = resource(fixture.get("response_file").asString());
            if (expected.get("parse_result").asString().equals("REJECT")) {
                assertEquals("FAILED", expected.get("generation_status").asString(), id);
                assertFalse(expected.get("parse_issue_tag").asString().isBlank(), id);
                for (String field : List.of("cards", "reviews", "excluded", "display_order", "summary")) {
                    assertTrue(expected.get(field).isEmpty(), id + ": " + field);
                }
                if (expected.get("parse_issue_tag").asString().equals("malformed_json")) {
                    assertThrows(RuntimeException.class, () -> JSON.readTree(raw), id);
                } else {
                    assertTrue(JSON.readTree(raw).get("items").isArray(), id);
                }
                continue;
            }

            assertEquals("ACCEPT", expected.get("parse_result").asString(), id);
            assertEquals("SUCCEEDED", expected.get("generation_status").asString(), id);
            JsonNode items = JSON.readTree(raw).get("items");
            assertTrue(items.isArray(), id);
            Set<Integer> classified = new HashSet<>();
            for (String field : List.of("cards", "reviews", "excluded")) {
                for (JsonNode result : expected.get(field)) {
                    int index = result.get("candidate_index").intValue();
                    assertTrue(index >= 0 && index < items.size(), id);
                    assertTrue(classified.add(index), id + ": duplicate candidate " + index);
                    if (!field.equals("cards"))
                        assertFalse(result.get("issue_tags").isEmpty(), id);
                }
            }
            assertEquals(items.size(), classified.size(), id);

            List<JsonNode> ordered = new ArrayList<>();
            expected.get("cards").forEach(ordered::add);
            ordered.sort(Comparator.comparingInt((JsonNode card) ->
                            card.at("/resolved_anchors/0/line_id").intValue())
                    .thenComparingInt(
                            card -> card.at("/resolved_anchors/0/start").intValue())
                    .thenComparingInt(card -> card.get("candidate_index").intValue()));
            assertEquals(ordered.size(), expected.get("display_order").size(), id);
            for (int i = 0; i < ordered.size(); i++) {
                assertEquals(
                        ordered.get(i).get("candidate_index").intValue(),
                        expected.get("display_order").get(i).intValue(),
                        id);
            }
        }
    }

    @Test
    void expectedCardAnchorsUseActualUtf16SourceRangesAndOnlyValidBoxes() throws IOException {
        for (JsonNode fixture : cases()) {
            if (!fixture.at("/expected/parse_result").asString().equals("ACCEPT")) continue;
            String id = fixture.get("id").asString();
            JsonNode response =
                    JSON.readTree(resource(fixture.get("response_file").asString()));
            JsonNode lines = fixture.at("/context/transcription_lines");
            for (JsonNode card : fixture.at("/expected/cards")) {
                JsonNode item =
                        response.get("items").get(card.get("candidate_index").intValue());
                JsonNode anchors = item.get("anchors");
                JsonNode resolved = card.get("resolved_anchors");
                assertEquals(anchors.size(), resolved.size(), id);
                List<String> originals = new ArrayList<>();
                for (int i = 0; i < anchors.size(); i++) {
                    JsonNode source = anchors.get(i);
                    JsonNode range = resolved.get(i);
                    int lineId = source.get("line_id").intValue();
                    String original = source.get("original").asString();
                    String line = lines.get(lineId - 1).get("text").asString();
                    int start = range.get("start").intValue();
                    int end = range.get("end").intValue();
                    assertEquals(lineId, range.get("line_id").intValue(), id);
                    assertEquals(original, line.substring(start, end), id);
                    int cursor = 0;
                    for (int occurrence = 0;
                            occurrence < source.get("occurrence").intValue();
                            occurrence++) {
                        int found = line.indexOf(original, cursor);
                        assertTrue(found >= 0, id);
                        if (occurrence + 1 == source.get("occurrence").intValue()) assertEquals(start, found, id);
                        cursor = found + original.length();
                    }
                    originals.add(original);
                }
                assertEquals(
                        String.join("\n", originals), card.get("original_text").asString(), id);
                assertEquals(
                        !card.get("issue_tags").isEmpty(),
                        card.get("requires_review").asBoolean(),
                        id);
                for (JsonNode box : card.get("valid_target_boxes")) {
                    assertEquals(4, box.size(), id);
                    double left = box.get(0).doubleValue();
                    double top = box.get(1).doubleValue();
                    double right = box.get(2).doubleValue();
                    double bottom = box.get(3).doubleValue();
                    assertTrue(0 <= left && left < right && right <= 1, id);
                    assertTrue(0 <= top && top < bottom && bottom <= 1, id);
                }
                if (item.get("action").asString().equals("GUIDANCE")) {
                    assertTrue(card.get("annotation").isNull(), id);
                    assertFalse(card.get("auto_place").asBoolean(), id);
                } else {
                    boolean correction = item.get("feedback_type").asString().equals("CORRECTION");
                    assertEquals(
                            correction ? "red" : "blue",
                            card.at("/annotation/color").asString(),
                            id);
                    assertEquals(
                            item.at("/edit/text").asString(),
                            card.at("/annotation/correction_text").asString(),
                            id);
                }
            }
        }
    }

    @Test
    void summaryCountsMatchTheExpectedCardsAndOmissionsWithoutCountingReviewItems() throws IOException {
        for (JsonNode fixture : cases()) {
            if (!fixture.at("/expected/parse_result").asString().equals("ACCEPT")) continue;
            String id = fixture.get("id").asString();
            JsonNode expected = fixture.get("expected");
            JsonNode items = JSON.readTree(resource(fixture.get("response_file").asString()))
                    .get("items");
            Set<String> seen = new HashSet<>();
            for (JsonNode summary : expected.get("summary")) {
                String area = summary.get("area").asString();
                assertTrue(AREAS.contains(area) && seen.add(area), id);
                int cards = 0;
                int omitted = 0;
                for (JsonNode result : expected.get("cards")) {
                    if (items.get(result.get("candidate_index").intValue())
                            .get("area")
                            .asString()
                            .equals(area)) {
                        cards++;
                    }
                }
                for (String field : List.of("reviews", "excluded")) {
                    for (JsonNode result : expected.get(field)) {
                        JsonNode item = items.get(result.get("candidate_index").intValue());
                        if (!item.get("action").asString().equals("REVIEW")
                                && item.get("area").asString().equals(area)) omitted++;
                    }
                }
                assertEquals(cards, summary.get("validated_count").intValue(), id);
                assertEquals(cards, summary.get("displayed_count").intValue(), id);
                assertEquals(omitted, summary.get("omitted_count").intValue(), id);
                assertEquals(
                        summary.get("level").isNull() ? 0 : 7,
                        summary.get("maximum_count").intValue(),
                        id);
                assertTrue(cards <= summary.get("maximum_count").intValue(), id);
            }
            assertEquals(AREAS, seen, id);
        }
    }

    @Test
    void levelComparisonPairsKeepTheSameSourceAndAreaSelection() throws IOException {
        for (String group : List.of("same-objective-error", "valid-but-monotonous-structure")) {
            List<JsonNode> pair = cases().stream()
                    .filter(fixture -> fixture.has("comparison_group")
                            && fixture.get("comparison_group").asString().equals(group))
                    .toList();
            assertEquals(2, pair.size(), group);
            JsonNode standard = pair.stream()
                    .filter(fixture -> fixture.at("/context/selected_areas/0/level")
                            .asString()
                            .equals("STANDARD"))
                    .findFirst()
                    .orElseThrow();
            JsonNode advanced = pair.stream()
                    .filter(fixture -> fixture.at("/context/selected_areas/0/level")
                            .asString()
                            .equals("ADVANCED"))
                    .findFirst()
                    .orElseThrow();
            assertEquals(standard.at("/context/transcription_lines"), advanced.at("/context/transcription_lines"));
            assertEquals(standard.at("/context/selected_areas/0/area"), advanced.at("/context/selected_areas/0/area"));
            JsonNode standardResponse =
                    JSON.readTree(resource(standard.get("response_file").asString()));
            JsonNode advancedResponse =
                    JSON.readTree(resource(advanced.get("response_file").asString()));
            if (group.equals("same-objective-error")) {
                assertEquals(standardResponse, advancedResponse);
            } else {
                assertTrue(standardResponse.get("items").isEmpty());
                assertEquals(
                        "SUGGESTION",
                        advancedResponse.at("/items/0/feedback_type").asString());
            }
        }
    }

    @Test
    void corpusCoversThePlannedActionsBoundariesAndFailureClasses() throws IOException {
        Set<String> tags = new HashSet<>();
        for (JsonNode fixture : cases()) fixture.get("tags").forEach(tag -> tags.add(tag.asString()));
        assertTrue(tags.containsAll(Set.of(
                "INSERT_BEFORE",
                "INSERT_AFTER",
                "DELETE",
                "REPLACE",
                "RESTRUCTURE",
                "GUIDANCE",
                "REVIEW",
                "EMPTY_ITEMS",
                "LEVEL_COMPARISON",
                "MULTILINE",
                "REPEATED_OCCURRENCE",
                "UTF16",
                "EMPTY_LINE",
                "UNCERTAIN_LOCATION",
                "PARTIAL_INVALID_BOXES",
                "CARD_LIMIT",
                "CORRECTION_PRIORITY",
                "STANDARD_SUGGESTION",
                "DISABLED_AREA",
                "AREA_SCOPE_VIOLATION",
                "ANCHOR_MISMATCH",
                "NONCONTIGUOUS_ANCHORS",
                "EDIT_MISMATCH",
                "INVALID_ACTION_FIELDS",
                "GUIDANCE_TOO_LONG",
                "INSERT_SOURCE_CHANGED",
                "MISSING_FIELD",
                "LEGACY_FIELDS",
                "ADDITIONAL_FIELD",
                "MALFORMED_JSON")));
    }

    private static List<JsonNode> cases() throws IOException {
        List<JsonNode> cases = new ArrayList<>();
        JSON.readTree(resource("cases.json")).get("cases").forEach(cases::add);
        assertFalse(cases.isEmpty());
        return cases;
    }

    private static String resource(String relative) throws IOException {
        try (var input = AiCorrectionFixtureResourcesTest.class.getResourceAsStream(ROOT + relative)) {
            assertNotNull(input, relative);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
