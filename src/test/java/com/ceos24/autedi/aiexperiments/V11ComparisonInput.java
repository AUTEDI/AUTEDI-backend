package com.ceos24.autedi.aiexperiments;

import com.ceos24.autedi.domain.generation.contract.v1_1.AiArea;
import com.ceos24.autedi.domain.generation.contract.v1_1.validation.AiCorrectionLevel;
import com.ceos24.autedi.domain.generation.contract.v1_1.validation.AiLocationContext;
import com.ceos24.autedi.domain.generation.contract.v1_1.validation.AiValidationContext;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 이미지·전사를 한 번 읽어 두 비교 요청에 같은 입력을 제공한다. 키·API·DB를 사용하지 않는다. */
final class V11ComparisonInput {
    static final JsonMapper JSON = JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .build();

    enum Category {
        GRAMMAR_ERROR,
        IMPROVABLE,
        CLEAN
    }

    record Prepared(
            String id,
            Category category,
            Path imagePath,
            Path transcriptionPath,
            AiLocationContext location,
            Set<AiArea> areas,
            List<String> rubric,
            byte[] png,
            int width,
            int height,
            int orientation,
            String imageHash,
            String transcriptionHash) {
        Prepared {
            areas = Set.copyOf(areas);
            rubric = List.copyOf(rubric);
            png = png.clone();
        }

        @Override
        public byte[] png() {
            return png.clone();
        }

        AiValidationContext context(AiCorrectionLevel level) {
            var selected = new EnumMap<AiArea, AiCorrectionLevel>(AiArea.class);
            areas.forEach(area -> selected.put(area, level));
            return new AiValidationContext(location, selected);
        }
    }

    static List<Prepared> load(Path manifest, String selectedCase) throws Exception {
        Path source = manifest.toAbsolutePath().normalize();
        JsonNode root = JSON.readTree(readText(source));
        fields(root, Set.of("format_version", "description", "cases"));
        if (!root.path("format_version").isIntegralNumber()
                || !root.path("format_version").canConvertToInt()
                || root.path("format_version").intValue() != 1
                || !root.path("description").isString()
                || !root.path("cases").isArray()
                || root.path("cases").isEmpty()
                || root.path("cases").size() > 10) {
            throw new IllegalArgumentException("실험 manifest 버전·설명·사례 목록을 확인하세요. 사례는 1~10개입니다.");
        }
        Set<String> ids = new HashSet<>();
        List<Prepared> prepared = new ArrayList<>();
        for (JsonNode item : root.get("cases")) {
            fields(item, Set.of("id", "category", "image", "transcription", "page_number", "areas", "rubric"));
            String id = text(item, "id");
            if (!id.matches("[A-Za-z0-9][A-Za-z0-9_-]{0,63}") || !ids.add(id)) {
                throw new IllegalArgumentException("사례 ID는 안전한 고유 영숫자·_- 이름이어야 합니다.");
            }
            Category category = Category.valueOf(text(item, "category"));
            if (!item.get("page_number").isIntegralNumber()
                    || !item.get("page_number").canConvertToInt()
                    || item.get("page_number").intValue() < 1) throw new IllegalArgumentException("페이지 번호가 잘못됐습니다.");
            if (!item.get("areas").isArray() || item.get("areas").isEmpty())
                throw new IllegalArgumentException("영역을 선택하세요.");
            Set<AiArea> areas = EnumSet.noneOf(AiArea.class);
            for (JsonNode area : item.get("areas")) {
                if (!area.isString() || !areas.add(AiArea.valueOf(area.asString())))
                    throw new IllegalArgumentException("중복·잘못된 영역입니다.");
            }
            if (!item.get("rubric").isArray() || item.get("rubric").isEmpty())
                throw new IllegalArgumentException("사람이 평가할 기준이 필요합니다.");
            List<String> rubric = new ArrayList<>();
            for (JsonNode criterion : item.get("rubric")) {
                if (!criterion.isString() || criterion.asString().isBlank())
                    throw new IllegalArgumentException("빈 평가 기준입니다.");
                rubric.add(criterion.asString());
            }
            String imageName = text(item, "image");
            String transcriptName = text(item, "transcription");
            if (selectedCase != null && !selectedCase.equals(id)) continue;
            Path imagePath = source.getParent().resolve(imageName).normalize();
            Path transcriptPath = source.getParent().resolve(transcriptName).normalize();
            String transcript = readText(transcriptPath);
            AiLocationContext location =
                    AiLocationContext.fromText(item.get("page_number").intValue(), transcript);
            var image = OcrTestImage.read(imagePath);
            prepared.add(new Prepared(
                    id,
                    category,
                    imagePath,
                    transcriptPath,
                    location,
                    areas,
                    rubric,
                    image.png(),
                    image.image().getWidth(),
                    image.image().getHeight(),
                    image.orientation(),
                    hash(image.png()),
                    hash(String.join("\n", location.transcriptionLines()).getBytes(StandardCharsets.UTF_8))));
        }
        if (prepared.isEmpty()) throw new IllegalArgumentException("선택한 실험 사례가 없습니다.");
        return List.copyOf(prepared);
    }

    static String hash(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    static String readText(Path path) throws Exception {
        if (Files.size(path) > 1024 * 1024) throw new IllegalArgumentException("텍스트 파일 제한은 1MB입니다.");
        String text = Files.readString(path, StandardCharsets.UTF_8);
        return text.startsWith("\uFEFF") ? text.substring(1) : text;
    }

    private static String text(JsonNode node, String key) {
        if (!node.path(key).isString() || node.get(key).asString().isBlank())
            throw new IllegalArgumentException("필수 문자열: " + key);
        return node.get(key).asString();
    }

    private static void fields(JsonNode node, Set<String> expected) {
        if (node == null
                || !node.isObject()
                || node.size() != expected.size()
                || !expected.stream().allMatch(node::has)) {
            throw new IllegalArgumentException("실험 설정의 필드가 올바르지 않습니다. 필요 필드: " + expected);
        }
    }
}
