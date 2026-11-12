package com.ceos24.autedi.domain.generation.contract.v1_1;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.IOException;
import java.lang.reflect.RecordComponent;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Schema와 DTO의 필드·wire enum·null 규칙을 확인한다. 파서·문맥 검증기를 대신하지 않는다. */
class AiCorrectionResponseSchemaTest {
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Test
    void identifiesTheNewContractAndRequiresExactlyTheRecordFields() throws IOException {
        JsonNode schema = schema();
        assertEquals(
                "https://json-schema.org/draft/2020-12/schema",
                schema.get("$schema").asString());
        assertEquals("urn:autedi:ai:contract:v1.1", schema.get("$id").asString());
        assertEquals(AiCorrectionContract.CONTRACT_ID, schema.get("title").asString());
        assertObjectFields(schema, AiCorrectionResponse.class);
        assertObjectFields(schema.at("/$defs/item"), AiCorrectionItem.class);
        assertObjectFields(schema.at("/$defs/anchor"), AiAnchor.class);
        assertObjectFields(schema.at("/$defs/edit"), AiEdit.class);
        assertEquals(12, schema.at("/$defs/item/properties").size());
        assertEquals("#/$defs/item", schema.at("/properties/items/items/$ref").asString());
        assertEquals(
                "#/$defs/anchor",
                schema.at("/$defs/item/properties/anchors/items/$ref").asString());
        assertEquals(
                "#/$defs/box",
                schema.at("/$defs/item/properties/target_boxes/items/$ref").asString());
        assertEquals(
                "#/$defs/edit",
                schema.at("/$defs/item/properties/edit/anyOf/0/$ref").asString());
    }

    @Test
    void enumWireValuesMatchTheSchemaIncludingNullableScopeAndPosition() throws IOException {
        JsonNode properties = schema().at("/$defs/item/properties");
        assertEnum(properties.at("/action/enum"), AiAction.values(), false);
        assertEnum(properties.at("/feedback_type/enum"), AiFeedbackType.values(), true);
        assertEnum(properties.at("/area/enum"), AiArea.values(), true);
        assertEnum(properties.at("/issue_kinds/items/enum"), AiIssueKind.values(), false);
        assertEnum(properties.at("/revision_scope/enum"), AiRevisionScope.values(), true);
        JsonNode edit = schema().at("/$defs/edit/properties");
        assertEnum(edit.at("/operation/enum"), AiEditOperation.values(), false);
        assertEnum(edit.at("/position/enum"), AiEditPosition.values(), true);
        assertEquals("grammar", JSON.valueToTree(AiIssueKind.GRAMMAR).asString());
        assertEquals(
                "sentence_structure",
                JSON.valueToTree(AiIssueKind.SENTENCE_STRUCTURE).asString());
        assertEquals(
                "SENTENCE_GROUP",
                JSON.valueToTree(AiRevisionScope.SENTENCE_GROUP).asString());
    }

    @Test
    void supportsEmptyResultsReviewArraysDeletionAndExplicitNulls() throws IOException {
        JsonNode schema = schema();
        JsonNode properties = schema.at("/$defs/item/properties");
        assertFalse(schema.at("/properties/items").has("minItems"));
        assertFalse(schema.at("/properties/items").has("maxItems"));
        assertEquals("array", properties.at("/anchors/type").asString());
        assertFalse(properties.get("anchors").has("minItems"));
        assertFalse(properties.get("anchors").has("maxItems"));
        assertEquals(JSON.readTree("[\"string\",\"null\"]"), properties.at("/replacement/type"));
        assertEquals(JSON.readTree("[\"string\",\"null\"]"), properties.at("/guidance/type"));
        assertEquals(JSON.readTree("[\"string\",\"null\"]"), properties.at("/revision_scope/type"));
        assertEquals("null", properties.at("/edit/anyOf/1/type").asString());
        assertFalse(properties.get("replacement").has("minLength"));
        assertEquals("string", schema.at("/$defs/edit/properties/text/type").asString());
        assertFalse(schema.at("/$defs/edit/properties/text").has("minLength"));
        assertEquals(JSON.readTree("[\"string\",\"null\"]"), schema.at("/$defs/edit/properties/position/type"));
        assertEquals(1, properties.at("/issue_kinds/minItems").intValue());
        assertTrue(properties.at("/issue_kinds/uniqueItems").asBoolean());
    }

    @Test
    void definesSingleLineAnchorsAndFourNormalizedBoxCoordinates() throws IOException {
        JsonNode schema = schema();
        JsonNode anchor = schema.at("/$defs/anchor/properties");
        assertEquals(1, anchor.at("/line_id/minimum").intValue());
        assertEquals(Integer.MAX_VALUE, anchor.at("/line_id/maximum").intValue());
        assertEquals(1, anchor.at("/occurrence/minimum").intValue());
        assertEquals(Integer.MAX_VALUE, anchor.at("/occurrence/maximum").intValue());
        assertEquals(1, anchor.at("/original/minLength").intValue());
        Pattern singleLine = Pattern.compile(anchor.at("/original/pattern").asString());
        assertTrue(singleLine.matcher("running").matches());
        assertTrue(singleLine.matcher(" student's words ").matches());
        assertFalse(singleLine.matcher("").matches());
        assertFalse(singleLine.matcher("two\nlines").matches());
        assertFalse(singleLine.matcher("two\rlines").matches());
        JsonNode box = schema.at("/$defs/box");
        assertEquals("array", box.get("type").asString());
        assertEquals(4, box.get("minItems").intValue());
        assertEquals(4, box.get("maxItems").intValue());
        assertEquals("number", box.at("/items/type").asString());
        assertEquals(0, box.at("/items/minimum").intValue());
        assertEquals(1, box.at("/items/maximum").intValue());
    }

    private static void assertObjectFields(JsonNode definition, Class<?> dto) {
        assertEquals("object", definition.get("type").asString());
        assertFalse(definition.get("additionalProperties").asBoolean());
        Set<String> expected = new HashSet<>();
        for (RecordComponent component : dto.getRecordComponents()) {
            JsonProperty property = component.getAccessor().getAnnotation(JsonProperty.class);
            expected.add(property == null ? component.getName() : property.value());
        }
        Set<String> required = new HashSet<>();
        definition.get("required").forEach(field -> required.add(field.asString()));
        assertEquals(expected, required);
        assertEquals(expected.size(), definition.get("required").size());
        assertEquals(expected.size(), definition.get("properties").size());
        expected.forEach(field -> assertTrue(definition.get("properties").has(field), field));
    }

    private static void assertEnum(JsonNode allowed, Enum<?>[] values, boolean nullable) {
        Set<JsonNode> expected = new HashSet<>();
        Arrays.stream(values).forEach(value -> expected.add(JSON.valueToTree(value)));
        if (nullable) expected.add(JSON.nullNode());
        Set<JsonNode> actual = new HashSet<>();
        allowed.forEach(actual::add);
        assertEquals(expected, actual);
        assertEquals(expected.size(), allowed.size());
    }

    private static JsonNode schema() throws IOException {
        try (var input =
                AiCorrectionResponseSchemaTest.class.getResourceAsStream(AiCorrectionContract.SCHEMA_RESOURCE)) {
            assertNotNull(input);
            return JSON.readTree(new String(input.readAllBytes(), StandardCharsets.UTF_8));
        }
    }
}
