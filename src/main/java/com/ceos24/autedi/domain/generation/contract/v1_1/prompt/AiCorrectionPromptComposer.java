package com.ceos24.autedi.domain.generation.contract.v1_1.prompt;

import com.ceos24.autedi.domain.generation.contract.v1_1.AiArea;
import com.ceos24.autedi.domain.generation.contract.v1_1.AiCorrectionContract;
import com.ceos24.autedi.domain.generation.contract.v1_1.validation.AiCorrectionLevel;
import com.ceos24.autedi.domain.generation.contract.v1_1.validation.AiValidationContext;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import tools.jackson.databind.json.JsonMapper;

/** v1.1 기본본·압축본을 독립적으로 조립한다. 실제 모델·HTTP·DB·현재 v1 생성 경로에 접근하지 않는다. */
public final class AiCorrectionPromptComposer {
    public static final String MODEL = "gpt-6.1-terra";
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String SCHEMA = resource(AiCorrectionContract.SCHEMA_RESOURCE);

    public AiCorrectionPrompt compose(AiValidationContext context) {
        return compose(context, AiCorrectionPromptVariant.DEFAULT);
    }

    public AiCorrectionPrompt compose(AiValidationContext context, AiCorrectionPromptVariant variant) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(variant, "variant");
        String root = variant.resourceRoot();
        List<String> sections = new ArrayList<>();
        sections.add("# Common correction principles\n" + resource(root + "common-principles.txt"));
        sections.add("# Selected areas and independent levels\n"
                + "Only the AREA blocks below are enabled. Apply each area's own level independently.\n"
                + "Do not include disabled-area changes in another area or a combined proposal.");
        // 선택 Map 순서와 무관하게 영역 enum 순서로 조립한다. OFF 영역의 상세 파일은 읽지 않는다.
        for (AiArea area : AiArea.values()) {
            AiCorrectionLevel level = context.selectedAreas().get(area);
            if (level == null) continue;
            String header = "## AREA " + area + " | LEVEL " + level;
            String limits = "For this page and representative area: maximum " + level.maximumCount()
                    + " REPLACE+GUIDANCE items; REVIEW excluded; no target or minimum.";
            String name = area.name().toLowerCase(Locale.ROOT).replace('_', '-') + "-"
                    + level.name().toLowerCase(Locale.ROOT) + ".txt";
            sections.add(header + "\n" + limits + "\n" + resource(root + "areas/" + name));
        }
        if (context.selectedAreas().isEmpty()) {
            sections.add("No correction areas are enabled. Return no REPLACE or GUIDANCE items.\n"
                    + "Return REVIEW only for genuine source-reading issues; otherwise return items=[].");
        }
        sections.add("# Contract v1.1 output\n" + resource(root + "output-contract.txt"));
        sections.add("# Anchors and target boxes\n" + resource(root + "anchor-boxes.txt"));
        sections.add("# Edit operations\n" + resource(root + "edit-operations.txt"));

        List<NumberedLine> lines = new ArrayList<>();
        List<String> transcription = context.location().transcriptionLines();
        for (int index = 0; index < transcription.size(); index++) {
            lines.add(new NumberedLine(index + 1, transcription.get(index)));
        }
        String inputText = JSON.writeValueAsString(
                new TranscriptionInput(context.location().pageNumber(), lines));
        return new AiCorrectionPrompt(
                MODEL,
                AiCorrectionContract.CONTRACT_ID,
                variant.promptVersion(),
                variant,
                context.selectedAreas(),
                String.join("\n\n", sections) + "\n",
                inputText,
                SCHEMA);
    }

    private static String resource(String path) {
        try (var stream = AiCorrectionPromptComposer.class.getResourceAsStream(path)) {
            if (stream == null) throw new IllegalStateException("v1.1 프롬프트 리소스가 없습니다: " + path);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8)
                    .replace("\r\n", "\n")
                    .replace('\r', '\n')
                    .strip();
        } catch (IOException e) {
            throw new IllegalStateException("v1.1 프롬프트 리소스를 읽을 수 없습니다: " + path, e);
        }
    }

    private record NumberedLine(@JsonProperty("line_id") int lineId, String text) {}

    private record TranscriptionInput(
            @JsonProperty("page_number") int pageNumber,
            @JsonProperty("transcription_lines") List<NumberedLine> transcriptionLines) {}
}
