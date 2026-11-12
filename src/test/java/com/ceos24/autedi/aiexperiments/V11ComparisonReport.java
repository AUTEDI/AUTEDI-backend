package com.ceos24.autedi.aiexperiments;

import static com.ceos24.autedi.aiexperiments.V11ComparisonInput.JSON;

import com.ceos24.autedi.domain.generation.contract.v1_1.AiAction;
import com.ceos24.autedi.domain.generation.contract.v1_1.AiCorrectionResponseParser;
import com.ceos24.autedi.domain.generation.contract.v1_1.AiFeedbackType;
import com.ceos24.autedi.domain.generation.contract.v1_1.validation.AiCorrectionLevel;
import com.ceos24.autedi.domain.generation.contract.v1_1.validation.AiCorrectionResponseValidator;
import com.ceos24.autedi.domain.generation.contract.v1_1.validation.AiValidationContext;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import tools.jackson.databind.JsonNode;

/** AI 원본과 서버 분리 결과를 함께 보존한다. 의미·교육적 품질에 자동 합격 판정을 내리지 않는다. */
final class V11ComparisonReport {
    record Assessment(String state, Map<String, Object> details, Set<String> corrections) {}

    static Assessment assess(V11ComparisonClient.Reply reply, AiValidationContext context) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("api", metrics(reply));
        if (!reply.successful()) {
            data.put("errorCode", reply.errorCode());
            return new Assessment("API_FAILED", data, Set.of());
        }
        try {
            var response = new AiCorrectionResponseParser().parse(reply.outputText());
            long corrections = response.items().stream()
                    .filter(i -> i.feedbackType() == AiFeedbackType.CORRECTION)
                    .count();
            long suggestions = response.items().stream()
                    .filter(i -> i.feedbackType() == AiFeedbackType.SUGGESTION)
                    .count();
            long reviews = response.items().stream()
                    .filter(i -> i.action() == AiAction.REVIEW)
                    .count();
            data.put(
                    "rawCounts",
                    Map.of(
                            "items",
                            response.items().size(),
                            "corrections",
                            corrections,
                            "suggestions",
                            suggestions,
                            "reviews",
                            reviews));
            data.put(
                    "rawStandardSuggestionCount",
                    context.selectedAreas().containsValue(AiCorrectionLevel.STANDARD) ? suggestions : 0L);
            Set<String> signatures = new LinkedHashSet<>();
            response.items().stream()
                    .filter(i -> i.feedbackType() == AiFeedbackType.CORRECTION)
                    .forEach(item -> {
                        Map<String, Object> signature = new LinkedHashMap<>();
                        signature.put("action", item.action());
                        signature.put("area", item.area());
                        signature.put("anchors", item.anchors());
                        signature.put("replacement", item.replacement());
                        signature.put("guidance", item.guidance());
                        signature.put("edit", item.edit());
                        signatures.add(JSON.writeValueAsString(signature));
                    });
            var checked = new AiCorrectionResponseValidator().validate(response, context);
            data.put(
                    "validatedCounts",
                    Map.of(
                            "cards",
                            checked.cards().size(),
                            "reviews",
                            checked.reviewIssues().size(),
                            "excluded",
                            checked.excludedItems().size()));
            data.put("summary", checked.summary());
            Map<String, Integer> issues = new LinkedHashMap<>();
            List<Map<String, Object>> candidates = new ArrayList<>();
            for (var candidate : checked.candidates()) {
                Map<String, Object> value = new LinkedHashMap<>();
                value.put("candidateIndex", candidate.candidateIndex());
                value.put("original", candidate.original());
                value.put("disposition", candidate.disposition());
                value.put("originalText", candidate.location().originalText());
                value.put("resolvedAnchors", candidate.location().resolvedAnchors());
                value.put("validTargetBoxes", candidate.location().validTargetBoxes());
                value.put("requiresReview", candidate.requiresReview());
                value.put("canAutoPlace", candidate.canAutoPlace());
                value.put("issues", candidate.issues());
                candidate.issues().forEach(issue -> issues.merge(issue.code().name(), 1, Integer::sum));
                candidates.add(value);
            }
            data.put("candidates", candidates);
            data.put("issueCounts", issues);
            data.put(
                    "displayOrder",
                    checked.cards().stream().map(i -> i.candidateIndex()).toList());
            return new Assessment("VALIDATED", data, Set.copyOf(signatures));
        } catch (IllegalArgumentException e) {
            data.put("errorCode", "PARSE_FAILED");
            data.put("parseError", "v1.1 응답 구조 검사를 통과하지 못했습니다. output.txt 원본을 확인하세요.");
            return new Assessment("PARSE_FAILED", data, Set.of());
        }
    }

    static Map<String, Object> metrics(V11ComparisonClient.Reply reply) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("elapsedMs", reply.elapsedMs());
        data.put("httpStatus", reply.httpStatus());
        data.put("requestId", reply.requestId());
        JsonNode envelope = reply.envelope();
        for (String name : List.of("id", "model", "service_tier", "status")) {
            data.put(
                    name,
                    envelope == null
                            ? null
                            : V11ComparisonClient.identifier(envelope.path(name).asString(null)));
        }
        Map<String, String> tokens = Map.of(
                "inputTokens",
                "/usage/input_tokens",
                "outputTokens",
                "/usage/output_tokens",
                "totalTokens",
                "/usage/total_tokens",
                "cachedTokens",
                "/usage/input_tokens_details/cached_tokens",
                "cacheWriteTokens",
                "/usage/input_tokens_details/cache_write_tokens",
                "cacheWrite30mTokens",
                "/usage/input_tokens_details/cache_write_tokens_details/ttl_30m_tokens",
                "cacheWrite12hTokens",
                "/usage/input_tokens_details/cache_write_tokens_details/ttl_12h_tokens",
                "reasoningTokens",
                "/usage/output_tokens_details/reasoning_tokens");
        tokens.forEach((name, pointer) -> {
            JsonNode value = envelope == null ? null : envelope.at(pointer);
            data.put(
                    name,
                    value != null && value.isIntegralNumber() && value.canConvertToLong() && value.longValue() >= 0
                            ? value.longValue()
                            : null);
        });
        return data;
    }

    static void writeJson(Path path, Object value) throws java.io.IOException {
        Files.writeString(
                path, JSON.writerWithDefaultPrettyPrinter().writeValueAsString(value) + "\n", StandardCharsets.UTF_8);
    }

    static void writeRun(
            Path directory,
            Map<String, Object> plan,
            List<Map<String, Object>> trials,
            Map<String, Assessment> assessments,
            boolean stopped)
            throws Exception {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("plan", plan);
        result.put(
                "state",
                stopped
                        ? "STOPPED_AFTER_API_FAILURE"
                        : !Boolean.TRUE.equals(plan.get("live"))
                                ? "CHECK_ONLY"
                                : trials.stream().anyMatch(t -> List.of("PLANNED", "STARTED")
                                                .contains(t.get("state")))
                                        ? "RUNNING_OR_INTERRUPTED"
                                        : "FINISHED");
        result.put(
                "requestAttempts",
                trials.stream()
                        .filter(t -> Boolean.TRUE.equals(t.get("requestAttempted")))
                        .count());
        result.put("qualityVerdict", "NOT_AUTOMATICALLY_EVALUATED");
        result.put("trials", trials);
        Map<String, List<Map<String, Object>>> pairs = new LinkedHashMap<>();
        trials.forEach(t -> pairs.computeIfAbsent(t.get("caseId") + ":" + t.get("repeat"), ignored -> new ArrayList<>())
                .add(t));
        List<Map<String, Object>> comparisons = new ArrayList<>();
        for (var entry : pairs.entrySet()) {
            List<Map<String, Object>> pair = entry.getValue();
            if (pair.size() != 2) continue;
            var first = assessments.get(pair.get(0).get("trialId").toString());
            var second = assessments.get(pair.get(1).get("trialId").toString());
            Map<String, Object> comparison = new LinkedHashMap<>();
            comparison.put("caseRepeat", entry.getKey());
            comparison.put("first", pair.get(0).get("label"));
            comparison.put("second", pair.get(1).get("label"));
            boolean comparable = first != null
                    && second != null
                    && first.state().equals("VALIDATED")
                    && second.state().equals("VALIDATED");
            comparison.put("comparisonAvailable", comparable);
            if (comparable) {
                Set<String> shared = new LinkedHashSet<>(first.corrections());
                shared.retainAll(second.corrections());
                Set<String> onlyFirst = new LinkedHashSet<>(first.corrections());
                onlyFirst.removeAll(second.corrections());
                Set<String> onlySecond = new LinkedHashSet<>(second.corrections());
                onlySecond.removeAll(first.corrections());
                comparison.put("sharedRawCorrections", shared);
                comparison.put("onlyFirstRawCorrections", onlyFirst);
                comparison.put("onlySecondRawCorrections", onlySecond);
                comparison.put("note", "사유·좌표를 제외한 정확한 수정 데이터 비교입니다. 의미가 같은 대안 여부는 사람이 확인합니다.");
            }
            comparisons.add(comparison);
        }
        result.put("comparisons", comparisons);
        result.put("timingByLabel", timings(trials, assessments));
        result.put("usageByLabel", usage(trials, assessments));
        writeJson(directory.resolve("comparison.json"), result);
        StringBuilder md = new StringBuilder("# v1.1 첨삭 비교 실험\n\n");
        md.append("- 실행 상태: ")
                .append(result.get("state"))
                .append("\n- 요청 시도: ")
                .append(result.get("requestAttempts"))
                .append("\n- 품질 판정: 사람이 확인해야 합니다. VALIDATED는 구조·정책 검사가 실행됐다는 뜻입니다.\n")
                .append("- 원본의 Standard SUGGESTION과 검증 후 제외 수를 함께 확인하세요.\n")
                .append("- [전체 비교·집계](comparison.json) / [고정 조건·계획](plan.json)\n\n")
                .append("| 사례 | 회차 | 조건 | 상태 | 원본 오류/제안/REVIEW | 카드/확인/제외 | ms | 입력/출력/추론 토큰 | 결과 |\n")
                .append("| --- | ---: | --- | --- | --- | --- | ---: | --- | --- |\n");
        for (Map<String, Object> trial : trials) {
            Assessment assessment = assessments.get(trial.get("trialId").toString());
            JsonNode detail = assessment == null ? JSON.readTree("{}") : JSON.valueToTree(assessment.details());
            md.append("| ")
                    .append(trial.get("caseId"))
                    .append(" | ")
                    .append(trial.get("repeat"))
                    .append(" | ")
                    .append(trial.get("label"))
                    .append(" | ")
                    .append(trial.get("state"))
                    .append(" | ")
                    .append(counts(detail.path("rawCounts"), List.of("corrections", "suggestions", "reviews")))
                    .append(" | ")
                    .append(counts(detail.path("validatedCounts"), List.of("cards", "reviews", "excluded")))
                    .append(" | ")
                    .append(display(detail.at("/api/elapsedMs")))
                    .append(" | ")
                    .append(counts(detail.path("api"), List.of("inputTokens", "outputTokens", "reasoningTokens")))
                    .append(" | [상세](")
                    .append(trial.get("trialId"))
                    .append("/result.json)");
            if (Files.exists(directory.resolve(trial.get("trialId").toString()).resolve("output.txt"))) {
                md.append(" / [원본](").append(trial.get("trialId")).append("/output.txt)");
            }
            md.append(" |\n");
        }
        md.append("\n추론 토큰은 출력에 포함됩니다. 누락된 사용량을 0으로 취급하지 않습니다. 시간·토큰 집계는 comparison.json에 있습니다.\n");
        md.append("\n## 사람이 확인할 기준\n\n");
        JsonNode caseData = JSON.valueToTree(plan.get("cases"));
        for (JsonNode item : caseData) {
            String id = item.get("id").asString();
            md.append("### ")
                    .append(id)
                    .append("\n\n")
                    .append("[이미지](")
                    .append(id)
                    .append("/image.png) / [확정 전사](")
                    .append(id)
                    .append("/transcription.txt)\n\n");
            for (JsonNode criterion : item.get("rubric")) {
                md.append("- [ ] ").append(markdownText(criterion.asString())).append('\n');
            }
            md.append('\n');
        }
        Files.writeString(directory.resolve("comparison.md"), md.toString(), StandardCharsets.UTF_8);
    }

    private static Map<String, Object> usage(List<Map<String, Object>> trials, Map<String, Assessment> assessments) {
        Map<String, List<Map<?, ?>>> groups = new LinkedHashMap<>();
        for (var trial : trials) {
            var samples = groups.computeIfAbsent(trial.get("label").toString(), ignored -> new ArrayList<>());
            Assessment assessment = assessments.get(trial.get("trialId").toString());
            if (assessment != null && !assessment.state().equals("API_FAILED")) {
                samples.add((Map<?, ?>) assessment.details().get("api"));
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        groups.forEach((label, samples) -> {
            Map<String, Object> metrics = new LinkedHashMap<>();
            metrics.put("completedApiResponses", samples.size());
            for (String name : List.of(
                    "inputTokens",
                    "outputTokens",
                    "totalTokens",
                    "cachedTokens",
                    "cacheWriteTokens",
                    "cacheWrite30mTokens",
                    "cacheWrite12hTokens",
                    "reasoningTokens")) {
                long known = 0;
                long sum = 0;
                for (var sample : samples) {
                    if (sample.get(name) instanceof Number value) {
                        known++;
                        sum += value.longValue();
                    }
                }
                Map<String, Object> totals = new LinkedHashMap<>();
                totals.put("knownSamples", known);
                totals.put("sumKnown", sum);
                totals.put("total", !samples.isEmpty() && known == samples.size() ? sum : null);
                metrics.put(name, totals);
            }
            result.put(label, metrics);
        });
        return result;
    }

    private static String markdownText(String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\\", "\\\\")
                .replace("[", "\\[")
                .replace("]", "\\]")
                .replace("*", "\\*")
                .replace("_", "\\_")
                .replace("`", "\\`")
                .replace("\r", " ")
                .replace("\n", " ");
    }

    private static Map<String, Object> timings(List<Map<String, Object>> trials, Map<String, Assessment> assessments) {
        Map<String, List<Long>> groups = new LinkedHashMap<>();
        for (var trial : trials) {
            String label = trial.get("label").toString();
            groups.computeIfAbsent(label, ignored -> new ArrayList<>());
            Assessment a = assessments.get(trial.get("trialId").toString());
            if (a != null && !a.state().equals("API_FAILED"))
                groups.get(label).add(((Number) ((Map<?, ?>) a.details().get("api")).get("elapsedMs")).longValue());
        }
        Map<String, Object> result = new LinkedHashMap<>();
        groups.forEach((label, values) -> {
            List<Long> sorted = values.stream().sorted().toList();
            Map<String, Object> stats = new LinkedHashMap<>();
            stats.put("completedApiResponses", sorted.size());
            stats.put(
                    "meanMs",
                    sorted.isEmpty()
                            ? null
                            : sorted.stream()
                                    .mapToLong(Long::longValue)
                                    .average()
                                    .orElseThrow());
            stats.put(
                    "medianMs",
                    sorted.isEmpty()
                            ? null
                            : (sorted.get((sorted.size() - 1) / 2) / 2.0 + sorted.get(sorted.size() / 2) / 2.0));
            stats.put("minMs", sorted.isEmpty() ? null : sorted.getFirst());
            stats.put("maxMs", sorted.isEmpty() ? null : sorted.getLast());
            result.put(label, stats);
        });
        return result;
    }

    private static String counts(JsonNode node, List<String> names) {
        return String.join(" / ", names.stream().map(n -> display(node.path(n))).toList());
    }

    private static String display(JsonNode node) {
        return node.isMissingNode() || node.isNull() ? "—" : node.isString() ? node.asString() : node.toString();
    }
}
