package com.ceos24.autedi.domain.generation.contract.v1;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.IOException;
import java.lang.reflect.RecordComponent;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 스키마와 Java의 공개 필드·enum이 독립적인 변경으로 어긋나지 않는지 검사한다. */
class AiCorrectionResponseSchemaTest {
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Test
    void schemaRequiresExactlyTheDtoFieldsAndDisallowsAdditionalProperties() throws IOException {
        JsonNode schema = schema();
        assertEquals(AiCorrectionResponseParser.CONTRACT_ID, schema.get("title").asString());
        assertObjectFields(schema, AiCorrectionResponse.class);
        assertObjectFields(schema.at("/$defs/item"), AiCorrectionItem.class);
        assertObjectFields(schema.at("/$defs/anchor"), AiAnchor.class);
        assertEquals("#/$defs/item", schema.at("/properties/items/items/$ref").asString());
        assertEquals(
                "#/$defs/anchor",
                schema.at("/$defs/item/properties/anchor/anyOf/0/$ref").asString());
        assertEquals(
                "#/$defs/box",
                schema.at("/$defs/item/properties/boxes/items/$ref").asString());
    }

    @Test
    void schemaEnumValuesMatchJavaWireValuesIncludingNullableEnums() throws IOException {
        JsonNode properties = schema().at("/$defs/item/properties");
        assertEnum(properties.at("/action/enum"), AiAction.values(), false);
        assertEnum(properties.at("/feedback_type/enum"), AiFeedbackType.values(), true);
        assertEnum(properties.at("/area/enum"), AiArea.values(), true);
        assertEnum(properties.at("/issue_kinds/items/enum"), AiIssueKind.values(), false);
    }

    @Test
    void schemaKeepsExplicitNullsEmptyResultsAndFourNormalizedCoordinates() throws IOException {
        JsonNode schema = schema();
        assertFalse(schema.at("/properties/items").has("minItems"));
        JsonNode fields = schema.at("/$defs/item/properties");
        assertEquals(JSON.readTree("[\"string\",\"null\"]"), fields.at("/replacement/type"));
        assertEquals(JSON.readTree("[\"string\",\"null\"]"), fields.at("/guidance/type"));
        assertEquals("null", fields.at("/anchor/anyOf/1/type").asString());
        assertEquals(1, fields.at("/issue_kinds/minItems").intValue());
        assertTrue(fields.at("/issue_kinds/uniqueItems").asBoolean());
        JsonNode box = schema.at("/$defs/box");
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
        try (var stream =
                AiCorrectionResponseSchemaTest.class.getResourceAsStream(AiCorrectionResponseParser.SCHEMA_RESOURCE)) {
            assertNotNull(stream);
            return JSON.readTree(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
        }
    }
}
