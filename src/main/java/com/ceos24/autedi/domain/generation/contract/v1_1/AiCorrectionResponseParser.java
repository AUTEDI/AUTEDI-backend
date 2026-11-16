package com.ceos24.autedi.domain.generation.contract.v1_1;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import tools.jackson.core.JacksonException;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * v1.1 JSON의 필수 필드·타입·enum을 검사한 뒤 미검증 후보 DTO로 변환한다.
 * 원문·설정·동작 조합·수정문 일관성·좌표 면적·개수 제한은 후속 검증기 책임이다.
 */
public final class AiCorrectionResponseParser {
    private static final Set<String> ITEM_FIELDS = Set.of(
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
            "edit");
    private static final JsonMapper JSON = JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .build();

    public AiCorrectionResponse parse(String json) {
        if (json == null || json.isBlank()) throw invalid("$", "JSON 응답이 필요합니다.");
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
            throw new IllegalArgumentException(AiCorrectionContract.CONTRACT_ID + ": JSON 구조를 읽을 수 없습니다.", e);
        }
    }

    private static void checkItem(JsonNode item, String path) {
        requireObject(item, ITEM_FIELDS, path);
        requireEnum(item.get("action"), AiAction.class, false, path + ".action");
        requireEnum(item.get("feedback_type"), AiFeedbackType.class, true, path + ".feedback_type");
        requireEnum(item.get("area"), AiArea.class, true, path + ".area");
        requireText(item.get("replacement"), true, path + ".replacement");
        requireText(item.get("guidance"), true, path + ".guidance");
        requireText(item.get("reason"), false, path + ".reason");
        require(!item.get("reason").asString().isEmpty(), path + ".reason", "빈 문자열은 허용하지 않습니다.");
        require(item.get("location_uncertain").isBoolean(), path + ".location_uncertain", "boolean이어야 합니다.");

        JsonNode issueKinds = item.get("issue_kinds");
        require(issueKinds.isArray() && !issueKinds.isEmpty(), path + ".issue_kinds", "1개 이상의 문제 종류가 필요합니다.");
        Set<String> seen = new HashSet<>();
        for (int index = 0; index < issueKinds.size(); index++) {
            JsonNode kind = issueKinds.get(index);
            String kindPath = path + ".issue_kinds[" + index + "]";
            requireEnum(kind, AiIssueKind.class, false, kindPath);
            require(seen.add(kind.asString()), kindPath, "중복된 문제 종류는 허용하지 않습니다.");
        }

        JsonNode anchors = item.get("anchors");
        require(anchors.isArray(), path + ".anchors", "배열이어야 합니다.");
        for (int index = 0; index < anchors.size(); index++) {
            checkAnchor(anchors.get(index), path + ".anchors[" + index + "]");
        }
        checkBoxes(item.get("target_boxes"), path + ".target_boxes");
        requireEnum(item.get("revision_scope"), AiRevisionScope.class, true, path + ".revision_scope");

        JsonNode edit = item.get("edit");
        if (!edit.isNull()) {
            requireObject(edit, Set.of("operation", "text", "position"), path + ".edit");
            requireEnum(edit.get("operation"), AiEditOperation.class, false, path + ".edit.operation");
            requireText(edit.get("text"), false, path + ".edit.text");
            requireEnum(edit.get("position"), AiEditPosition.class, true, path + ".edit.position");
        }
    }

    private static void checkAnchor(JsonNode anchor, String path) {
        requireObject(anchor, Set.of("line_id", "original", "occurrence"), path);
        requirePositiveInteger(anchor.get("line_id"), path + ".line_id");
        requirePositiveInteger(anchor.get("occurrence"), path + ".occurrence");
        requireText(anchor.get("original"), false, path + ".original");
        String original = anchor.get("original").asString();
        require(
                !original.isEmpty() && !original.contains("\n") && !original.contains("\r"),
                path + ".original",
                "한 줄의 비어 있지 않은 원문이어야 합니다.");
    }

    private static void checkBoxes(JsonNode boxes, String path) {
        require(boxes.isArray(), path, "배열이어야 합니다.");
        for (int index = 0; index < boxes.size(); index++) {
            JsonNode box = boxes.get(index);
            String boxPath = path + "[" + index + "]";
            require(box.isArray() && box.size() == 4, boxPath, "숫자 4개의 배열이어야 합니다.");
            for (int coordinateIndex = 0; coordinateIndex < box.size(); coordinateIndex++) {
                JsonNode coordinate = box.get(coordinateIndex);
                String coordinatePath = boxPath + "[" + coordinateIndex + "]";
                require(coordinate.isNumber(), coordinatePath, "좌표는 숫자여야 합니다.");
                double value = coordinate.doubleValue();
                require(Double.isFinite(value) && value >= 0 && value <= 1, coordinatePath, "좌표는 유한한 0~1 범위여야 합니다.");
            }
        }
    }

    private static <E extends Enum<E>> void requireEnum(JsonNode node, Class<E> type, boolean nullable, String path) {
        requireText(node, nullable, path);
        if (node.isNull()) return;
        boolean allowed = Arrays.stream(type.getEnumConstants())
                .anyMatch(value -> JSON.valueToTree(value).equals(node));
        require(allowed, path, "계약에 정의된 enum 값이어야 합니다.");
    }

    private static void requireObject(JsonNode node, Set<String> fields, String path) {
        require(node != null && node.isObject(), path, "객체여야 합니다.");
        require(node.size() == fields.size() && fields.stream().allMatch(node::has), path, "계약의 모든 필드만 포함해야 합니다.");
    }

    private static void requireText(JsonNode node, boolean nullable, String path) {
        require(node.isString() || nullable && node.isNull(), path, nullable ? "문자열 또는 null이어야 합니다." : "문자열이어야 합니다.");
    }

    private static void requirePositiveInteger(JsonNode node, String path) {
        require(node.isIntegralNumber() && node.canConvertToInt() && node.intValue() >= 1, path, "1 이상의 int 정수여야 합니다.");
    }

    private static void require(boolean condition, String path, String message) {
        if (!condition) throw invalid(path, message);
    }

    private static IllegalArgumentException invalid(String path, String message) {
        return new IllegalArgumentException(AiCorrectionContract.CONTRACT_ID + " " + path + ": " + message);
    }
}
