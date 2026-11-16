package com.ceos24.autedi.domain.generation.contract.v1.prompt;

import com.ceos24.autedi.domain.generation.contract.v1.AiArea;
import com.ceos24.autedi.domain.generation.contract.v1.AiCorrectionResponseParser;
import com.ceos24.autedi.domain.generation.contract.v1.validation.AiCorrectionLevel;
import com.ceos24.autedi.domain.generation.contract.v1.validation.AiValidationContext;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import tools.jackson.databind.json.JsonMapper;

/** 설정 스냅샷으로 첨삭 instructions와 번호가 붙은 전사 입력을 조립한다. 네트워크·키·DB에 접근하지 않는다. */
public final class AiCorrectionPromptComposer {
    public static final String MODEL = "gpt-6.1-terra";
    public static final String PROMPT_VERSION = "AUTEDI-CORRECTION-PROMPT-v1";

    private static final String ROOT = "/ai/prompt/v1/";
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String COMMON = resource(ROOT + "common-principles.txt");
    private static final String OUTPUT = resource(ROOT + "output-contract.txt");
    private static final String LOCATION = resource(ROOT + "anchor-boxes.txt");
    private static final String SCHEMA = resource(AiCorrectionResponseParser.SCHEMA_RESOURCE);

    public AiCorrectionPrompt compose(AiValidationContext context) {
        Objects.requireNonNull(context, "context");
        List<String> sections = new ArrayList<>();
        sections.add("# Common correction principles\n" + COMMON);
        sections.add("# Selected areas and independent levels\n"
                + "Apply each area's level independently. Levels describe intervention intensity, not student ability.\n"
                + "Only the enabled areas listed below may produce REPLACE or GUIDANCE items.\n"
                + "Never include a disabled area's changes under another area or in a combined proposal.");

        // enum 순서로 조립하므로 Map의 구현이나 삽입 순서에 영향을 받지 않는다.
        for (AiArea area : AiArea.values()) {
            AiCorrectionLevel level = context.selectedAreas().get(area);
            if (level == null) continue;
            String header = "## AREA " + area + " | LEVEL " + level;
            String limits = "For this page and representative area: recommended " + level.recommendedCount()
                    + " cards, maximum " + level.maximumCount() + ".\n"
                    + "Count REPLACE and GUIDANCE together; each item counts once regardless of issue_kinds or boxes.\n"
                    + "REVIEW does not use these slots. There is no minimum; never invent items to reach a target.";
            String name = area.name().toLowerCase(Locale.ROOT).replace('_', '-') + "-"
                    + level.name().toLowerCase(Locale.ROOT) + ".txt";
            sections.add(header + "\n" + limits + "\n" + resource(ROOT + "areas/" + name));
        }
        if (context.selectedAreas().isEmpty()) {
            sections.add("No correction areas are enabled. Return no REPLACE or GUIDANCE items.\n"
                    + "Return REVIEW only for genuine source-reading issues; otherwise return items=[].");
        }
        sections.add("# Contract v1 output\n" + OUTPUT);
        sections.add("# Anchor and boxes\n" + LOCATION);

        List<NumberedLine> lines = new ArrayList<>();
        for (int index = 0; index < context.transcriptionLines().size(); index++) {
            lines.add(new NumberedLine(index + 1, context.transcriptionLines().get(index)));
        }
        String inputText = JSON.writeValueAsString(new TranscriptionInput(context.pageNumber(), lines));
        return new AiCorrectionPrompt(
                MODEL,
                AiCorrectionResponseParser.CONTRACT_ID,
                PROMPT_VERSION,
                context.selectedAreas(),
                String.join("\n\n", sections) + "\n",
                inputText,
                SCHEMA);
    }

    private static String resource(String path) {
        try (var stream = AiCorrectionPromptComposer.class.getResourceAsStream(path)) {
            if (stream == null) throw new IllegalStateException("프롬프트 리소스가 없습니다: " + path);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8)
                    .replace("\r\n", "\n")
                    .strip();
        } catch (IOException e) {
            throw new IllegalStateException("프롬프트 리소스를 읽을 수 없습니다: " + path, e);
        }
    }

    private record NumberedLine(@JsonProperty("line_id") int lineId, String text) {}

    private record TranscriptionInput(
            @JsonProperty("page_number") int pageNumber,
            @JsonProperty("transcription_lines") List<NumberedLine> transcriptionLines) {}
}
