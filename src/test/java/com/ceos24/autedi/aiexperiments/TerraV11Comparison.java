package com.ceos24.autedi.aiexperiments;

import static com.ceos24.autedi.aiexperiments.V11ComparisonInput.JSON;

import com.ceos24.autedi.domain.generation.contract.v1_1.prompt.AiCorrectionPrompt;
import com.ceos24.autedi.domain.generation.contract.v1_1.prompt.AiCorrectionPromptComposer;
import com.ceos24.autedi.domain.generation.contract.v1_1.prompt.AiCorrectionPromptVariant;
import com.ceos24.autedi.domain.generation.contract.v1_1.validation.AiCorrectionLevel;
import com.ceos24.autedi.domain.generation.contract.v1_1.validation.AiValidationContext;
import io.github.cdimascio.dotenv.Dotenv;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** 수동 main 실험. 기본은 --check이며 --run에서만 명시적으로 유료 호출한다. 서비스·DB·전사는 실행하지 않는다. */
public final class TerraV11Comparison {
    enum Mode {
        LEVELS,
        PROMPTS
    }

    record Options(
            Path manifest,
            Path output,
            Mode mode,
            AiCorrectionPromptVariant variant,
            AiCorrectionLevel level,
            int repeats,
            String caseId,
            boolean live,
            V11ComparisonClient.Settings settings) {
        Options {
            java.util.Objects.requireNonNull(manifest);
            java.util.Objects.requireNonNull(mode);
            java.util.Objects.requireNonNull(variant);
            java.util.Objects.requireNonNull(level);
            java.util.Objects.requireNonNull(settings);
            if (repeats < 1 || repeats > 5) throw new IllegalArgumentException("반복 횟수는 1~5회입니다.");
        }
    }

    private record Trial(
            V11ComparisonInput.Prepared input,
            int repeat,
            String label,
            AiCorrectionLevel level,
            AiCorrectionPromptVariant variant,
            AiValidationContext context,
            AiCorrectionPrompt prompt) {
        String id() {
            return input.id() + "/r" + String.format(Locale.ROOT, "%02d", repeat) + "-"
                    + label.toLowerCase(Locale.ROOT);
        }
    }

    public static void main(String[] args) throws Exception {
        Options options = parse(args);
        if (!options.live()) {
            Path result = run(options, null);
            System.out.println("v1.1 입력 점검 완료. 실제 AI 호출: 0. 결과: " + result);
            return;
        }
        String key;
        try {
            key = Dotenv.configure()
                    .directory(Path.of("").toAbsolutePath().toString())
                    .ignoreIfMissing()
                    .load()
                    .get("OPENAI_API_KEY");
        } catch (RuntimeException e) {
            throw new IllegalStateException("API 키 설정을 읽을 수 없습니다. 키 값은 출력하지 않습니다.");
        }
        try (HttpClient http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build()) {
            var client = new V11ComparisonClient(http, V11ComparisonClient.ENDPOINT, key, options.settings());
            Path result = run(options, client);
            System.out.println("v1.1 비교 실행 종료. 완료·실패·사용량을 보고서에서 확인하세요: " + result);
            if ("STOPPED_AFTER_API_FAILURE"
                    .equals(JSON.readTree(Files.readString(result.resolve("comparison.json")))
                            .path("state")
                            .asString())) {
                throw new IllegalStateException("API 실패로 남은 호출을 중단했습니다. 결과 보고서를 확인하세요.");
            }
        }
    }

    static Options parse(String[] args) {
        Map<String, String> values = new LinkedHashMap<>();
        Set<String> flags = new HashSet<>();
        Set<String> valued = Set.of(
                "--manifest",
                "--output",
                "--mode",
                "--variant",
                "--level",
                "--repeats",
                "--case",
                "--reasoning",
                "--max-output-tokens",
                "--timeout-seconds");
        for (int i = 0; i < args.length; i++) {
            String name = args[i];
            if (!flags.add(name)) throw new IllegalArgumentException("중복 옵션: " + name);
            if (name.equals("--run") || name.equals("--check")) continue;
            if (!valued.contains(name) || i + 1 >= args.length || args[i + 1].startsWith("--"))
                throw new IllegalArgumentException("잘못된 실험 옵션: " + name);
            values.put(name, args[++i]);
        }
        if (flags.contains("--run") && flags.contains("--check"))
            throw new IllegalArgumentException("--run과 --check를 동시에 사용할 수 없습니다.");
        Mode mode = Mode.valueOf(values.getOrDefault("--mode", "LEVELS").toUpperCase(Locale.ROOT));
        if (mode == Mode.LEVELS && values.containsKey("--level")
                || mode == Mode.PROMPTS && values.containsKey("--variant")) {
            throw new IllegalArgumentException("LEVELS는 --variant를, PROMPTS는 --level을 고정합니다.");
        }
        return new Options(
                Path.of(values.getOrDefault("--manifest", "src/test/resources/ai/experiments/v1.1/cases.json")),
                values.containsKey("--output") ? Path.of(values.get("--output")) : null,
                mode,
                AiCorrectionPromptVariant.valueOf(
                        values.getOrDefault("--variant", "DEFAULT").toUpperCase(Locale.ROOT)),
                AiCorrectionLevel.valueOf(
                        values.getOrDefault("--level", "ADVANCED").toUpperCase(Locale.ROOT)),
                Integer.parseInt(values.getOrDefault("--repeats", "1")),
                values.get("--case"),
                flags.contains("--run"),
                new V11ComparisonClient.Settings(
                        values.getOrDefault("--reasoning", "medium"),
                        Integer.parseInt(values.getOrDefault("--max-output-tokens", "12000")),
                        Duration.ofSeconds(Long.parseLong(values.getOrDefault("--timeout-seconds", "180")))));
    }

    static Path run(Options options, V11ComparisonClient client) throws Exception {
        if (options.live() && client == null) throw new IllegalArgumentException("실제 실행에는 호출기가 필요합니다.");
        if (options.live() && !options.settings().equals(client.settings()))
            throw new IllegalArgumentException("계획과 호출기의 설정이 일치해야 합니다.");
        List<V11ComparisonInput.Prepared> inputs = V11ComparisonInput.load(options.manifest(), options.caseId());
        List<Trial> schedule = schedule(inputs, options);
        if (schedule.size() > 40) throw new IllegalArgumentException("한 실험은 최대 40회 호출 계획만 허용합니다. 사례·반복을 줄이세요.");
        Path directory;
        if (options.output() == null) {
            Path base = Files.createDirectories(Path.of("build", "ai-experiments", "v1.1"));
            directory = Files.createTempDirectory(base, options.live() ? "run-" : "check-")
                    .toAbsolutePath();
        } else {
            directory = options.output().toAbsolutePath().normalize();
            // 기존 결과·STARTED receipt를 덮어쓰거나 실행 재개로 재전송하지 않는다.
            Files.createDirectory(directory);
        }
        Map<String, Object> plan = new LinkedHashMap<>();
        plan.put("createdAt", Instant.now().toString());
        plan.put("live", options.live());
        plan.put("mode", options.mode());
        plan.put("plannedCalls", schedule.size());
        plan.put("repeats", options.repeats());
        plan.put("model", AiCorrectionPromptComposer.MODEL);
        plan.put("reasoningEffort", options.settings().reasoningEffort());
        plan.put("maxOutputTokens", options.settings().maxOutputTokens());
        plan.put("timeoutMs", options.settings().timeout().toMillis());
        plan.put("serviceTier", "default");
        plan.put("imageDetail", "high");
        plan.put("automaticRetries", false);
        plan.put("manifest", options.manifest().toAbsolutePath().normalize().toString());
        plan.put(
                "manifestDescription",
                JSON.readTree(V11ComparisonInput.readText(options.manifest()))
                        .get("description")
                        .asString());
        plan.put(
                "endpoint",
                client == null
                        ? V11ComparisonClient.ENDPOINT.toString()
                        : client.endpoint().toString());
        List<Map<String, Object>> cases = new ArrayList<>();
        for (var input : inputs) {
            Path caseDirectory = Files.createDirectory(directory.resolve(input.id()));
            Files.write(caseDirectory.resolve("image.png"), input.png(), StandardOpenOption.CREATE_NEW);
            Files.writeString(
                    caseDirectory.resolve("transcription.txt"),
                    String.join("\n", input.location().transcriptionLines()),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("id", input.id());
            data.put("category", input.category());
            data.put("areas", input.areas());
            data.put("rubric", input.rubric());
            data.put("sourceImage", input.imagePath().toString());
            data.put("sourceTranscription", input.transcriptionPath().toString());
            data.put("imageHash", input.imageHash());
            data.put("transcriptionHash", input.transcriptionHash());
            data.put("width", input.width());
            data.put("height", input.height());
            data.put("orientation", input.orientation());
            data.put("pageNumber", input.location().pageNumber());
            cases.add(data);
        }
        plan.put("cases", cases);
        V11ComparisonReport.writeJson(directory.resolve("plan.json"), plan);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Trial trial : schedule) {
            Path trialDirectory = Files.createDirectory(directory.resolve(trial.id()));
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("trialId", trial.id());
            row.put("caseId", trial.input().id());
            row.put("repeat", trial.repeat());
            row.put("executionOrder", rows.size() + 1);
            row.put("label", trial.label());
            row.put("level", trial.level());
            row.put("variant", trial.variant());
            row.put("promptVersion", trial.prompt().promptVersion());
            row.put("imageHash", trial.input().imageHash());
            row.put("transcriptionHash", trial.input().transcriptionHash());
            row.put("requestAttempted", false);
            row.put("state", options.live() ? "PLANNED" : "CHECK_ONLY");
            V11ComparisonReport.writeJson(trialDirectory.resolve("prompt.json"), trial.prompt());
            V11ComparisonReport.writeJson(
                    trialDirectory.resolve("transport-schema.json"),
                    V11ComparisonClient.transportSchema(trial.prompt()));
            V11ComparisonReport.writeJson(trialDirectory.resolve("receipt.json"), row);
            V11ComparisonReport.writeJson(trialDirectory.resolve("result.json"), row);
            rows.add(row);
        }
        Map<String, V11ComparisonReport.Assessment> assessments = new LinkedHashMap<>();
        boolean stopped = false;
        V11ComparisonReport.writeRun(directory, plan, rows, assessments, stopped);
        if (!options.live()) return directory;
        try {
            for (int i = 0; i < schedule.size(); i++) {
                Trial trial = schedule.get(i);
                Map<String, Object> row = rows.get(i);
                Path trialDirectory = directory.resolve(trial.id());
                if (stopped) {
                    row.put("state", "NOT_RUN");
                    V11ComparisonReport.writeJson(trialDirectory.resolve("receipt.json"), row);
                    V11ComparisonReport.writeJson(trialDirectory.resolve("result.json"), row);
                    continue;
                }
                row.put("state", "STARTED");
                row.put("requestAttempted", true);
                row.put("startedAt", Instant.now().toString());
                V11ComparisonReport.writeJson(trialDirectory.resolve("receipt.json"), row);
                V11ComparisonClient.Reply reply;
                try {
                    reply = client.call(trial.prompt(), trial.input());
                } catch (RuntimeException e) {
                    reply = new V11ComparisonClient.Reply(0, null, null, null, null, "CLIENT_FAILURE");
                }
                if (reply.envelope() != null)
                    V11ComparisonReport.writeJson(trialDirectory.resolve("envelope.json"), reply.envelope());
                if (reply.outputText() != null)
                    Files.writeString(
                            trialDirectory.resolve("output.txt"),
                            reply.outputText(),
                            StandardCharsets.UTF_8,
                            StandardOpenOption.CREATE_NEW);
                var assessed = V11ComparisonReport.assess(reply, trial.context());
                assessments.put(trial.id(), assessed);
                row.put("state", assessed.state());
                row.put("completedAt", Instant.now().toString());
                Map<String, Object> result = new LinkedHashMap<>(row);
                result.putAll(assessed.details());
                V11ComparisonReport.writeJson(trialDirectory.resolve("result.json"), result);
                V11ComparisonReport.writeJson(trialDirectory.resolve("receipt.json"), row);
                stopped = !reply.successful();
                V11ComparisonReport.writeRun(directory, plan, rows, assessments, stopped);
            }
        } finally {
            V11ComparisonReport.writeRun(directory, plan, rows, assessments, stopped);
        }
        return directory;
    }

    private static List<Trial> schedule(List<V11ComparisonInput.Prepared> inputs, Options options) {
        List<Trial> schedule = new ArrayList<>();
        var composer = new AiCorrectionPromptComposer();
        for (int caseIndex = 0; caseIndex < inputs.size(); caseIndex++) {
            var input = inputs.get(caseIndex);
            for (int repeat = 1; repeat <= options.repeats(); repeat++) {
                List<Trial> pair = new ArrayList<>();
                if (options.mode() == Mode.LEVELS) {
                    for (AiCorrectionLevel level : AiCorrectionLevel.values()) {
                        var context = input.context(level);
                        pair.add(new Trial(
                                input,
                                repeat,
                                level.name(),
                                level,
                                options.variant(),
                                context,
                                composer.compose(context, options.variant())));
                    }
                } else {
                    for (AiCorrectionPromptVariant variant : AiCorrectionPromptVariant.values()) {
                        var context = input.context(options.level());
                        pair.add(new Trial(
                                input,
                                repeat,
                                variant.name(),
                                options.level(),
                                variant,
                                context,
                                composer.compose(context, variant)));
                    }
                }
                if ((caseIndex + repeat - 1) % 2 != 0) Collections.reverse(pair);
                schedule.addAll(pair);
            }
        }
        return List.copyOf(schedule);
    }
}
