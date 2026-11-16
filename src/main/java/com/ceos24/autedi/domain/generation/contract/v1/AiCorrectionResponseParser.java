package com.ceos24.autedi.domain.generation.contract.v1;

import java.util.HashSet;
import java.util.Set;
import tools.jackson.core.JacksonException;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 모델 응답 본문을 읽는 독립 파서. API 응답 envelope나 기존 edits 형식은 받지 않는다.
 * 구조 검사 후에도 동작별 조합, 전사문, 좌표의 양수 면적, 설정 및 개수 제한의 후처리가 필요하다.
 */
public final class AiCorrectionResponseParser {
    public static final String CONTRACT_ID = "AUTEDI-AI-CONTRACT-v1";
    public static final String SCHEMA_RESOURCE = "/ai/contract/v1/correction-response.schema.json";

    private static final Set<String> ITEM_FIELDS = Set.of(
            "action",
            "feedback_type",
            "area",
            "issue_kinds",
            "anchor",
            "replacement",
            "guidance",
            "reason",
            "location_uncertain",
            "boxes");
    private static final JsonMapper JSON = JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .build();

    public AiCorrectionResponse parse(String json) {
        if (json == null || json.isBlank()) {
            throw invalid("$", "JSON 응답이 필요합니다.");
        }
        try {
            JsonNode root = JSON.readTree(json);
            requireObject(root, Set.of("items"), "$");
            JsonNode items = root.get("items");
            require(items.isArray(), "$.items", "배열이어야 합니다.");
            for (int index = 0; index < items.size(); index++) {
                checkItem(items.get(index), "$.items[" + index + "]");
            }
            return JSON.treeToValue(root, AiCorrectionResponse.class);
        } catch (JacksonException e) {
            throw new IllegalArgumentException(CONTRACT_ID + ": JSON 구조 또는 enum 값이 올바르지 않습니다.", e);
        }
    }

    private static void checkItem(JsonNode item, String path) {
        requireObject(item, ITEM_FIELDS, path);
        requireText(item.get("action"), false, path + ".action");
        requireText(item.get("feedback_type"), true, path + ".feedback_type");
        requireText(item.get("area"), true, path + ".area");
        requireText(item.get("replacement"), true, path + ".replacement");
        requireText(item.get("guidance"), true, path + ".guidance");
        requireText(item.get("reason"), false, path + ".reason");
        require(!item.get("reason").asString().isEmpty(), path + ".reason", "빈 문자열은 허용하지 않습니다.");
        require(item.get("location_uncertain").isBoolean(), path + ".location_uncertain", "boolean이어야 합니다.");

        JsonNode issueKinds = item.get("issue_kinds");
        require(issueKinds.isArray() && !issueKinds.isEmpty(), path + ".issue_kinds", "1개 이상의 문제 종류가 필요합니다.");
        Set<String> seen = new HashSet<>();
        for (JsonNode issueKind : issueKinds) {
            requireText(issueKind, false, path + ".issue_kinds");
            require(seen.add(issueKind.asString()), path + ".issue_kinds", "중복된 문제 종류는 허용하지 않습니다.");
        }

        JsonNode anchor = item.get("anchor");
        if (!anchor.isNull()) {
            requireObject(anchor, Set.of("line_id", "original", "occurrence"), path + ".anchor");
            requirePositiveInteger(anchor.get("line_id"), path + ".anchor.line_id");
            requirePositiveInteger(anchor.get("occurrence"), path + ".anchor.occurrence");
            requireText(anchor.get("original"), false, path + ".anchor.original");
            String original = anchor.get("original").asString();
            require(
                    !original.isEmpty() && !original.contains("\n") && !original.contains("\r"),
                    path + ".anchor.original",
                    "한 줄의 비어 있지 않은 원문이어야 합니다.");
        }

        JsonNode boxes = item.get("boxes");
        require(boxes.isArray(), path + ".boxes", "배열이어야 합니다.");
        for (int index = 0; index < boxes.size(); index++) {
            JsonNode box = boxes.get(index);
            String boxPath = path + ".boxes[" + index + "]";
            require(box.isArray() && box.size() == 4, boxPath, "숫자 4개의 배열이어야 합니다.");
            for (JsonNode coordinate : box) {
                require(coordinate.isNumber(), boxPath, "좌표는 숫자여야 합니다.");
                double value = coordinate.doubleValue();
                require(Double.isFinite(value) && value >= 0 && value <= 1, boxPath, "좌표는 0~1 범위여야 합니다.");
            }
        }
    }

    private static void requireObject(JsonNode node, Set<String> fields, String path) {
        require(node != null && node.isObject(), path, "객체여야 합니다.");
        require(node.size() == fields.size() && fields.stream().allMatch(node::has), path, "계약의 모든 필드만 포함해야 합니다.");
    }

    private static void requireText(JsonNode node, boolean nullable, String path) {
        require(node.isString() || nullable && node.isNull(), path, nullable ? "문자열 또는 null이어야 합니다." : "문자열이어야 합니다.");
    }

    private static void requirePositiveInteger(JsonNode node, String path) {
        require(node.isIntegralNumber() && node.canConvertToInt() && node.intValue() >= 1, path, "1 이상의 정수여야 합니다.");
    }

    private static void require(boolean condition, String path, String message) {
        if (!condition) {
            throw invalid(path, message);
        }
    }

    private static IllegalArgumentException invalid(String path, String message) {
        return new IllegalArgumentException(CONTRACT_ID + " " + path + ": " + message);
    }
}
