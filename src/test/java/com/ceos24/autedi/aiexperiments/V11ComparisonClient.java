package com.ceos24.autedi.aiexperiments;

import static com.ceos24.autedi.aiexperiments.V11ComparisonInput.JSON;

import com.ceos24.autedi.domain.generation.contract.v1_1.prompt.AiCorrectionPrompt;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/** v1.1 실험 전용 단일 호출. 자동 재시도·서비스 클라이언트·DB 의존성이 없다. */
final class V11ComparisonClient {
    static final URI ENDPOINT = URI.create("https://api.openai.com/v1/responses");

    record Settings(String reasoningEffort, int maxOutputTokens, Duration timeout) {
        Settings {
            if (!Set.of("none", "low", "medium", "high", "xhigh", "max").contains(reasoningEffort)
                    || maxOutputTokens < 256
                    || maxOutputTokens > 12000
                    || timeout == null
                    || timeout.isNegative()
                    || timeout.isZero()
                    || timeout.compareTo(Duration.ofMinutes(3)) > 0) {
                throw new IllegalArgumentException("추론 강도·출력 한도(256~12000)·제한 시간(최대 3분)을 확인하세요.");
            }
        }
    }

    record Reply(
            long elapsedMs,
            Integer httpStatus,
            String requestId,
            JsonNode envelope,
            String outputText,
            String errorCode) {
        boolean successful() {
            return errorCode == null;
        }
    }

    private final HttpClient http;
    private final URI endpoint;
    private final String apiKey;
    private final Settings settings;

    Settings settings() {
        return settings;
    }

    URI endpoint() {
        return endpoint;
    }

    V11ComparisonClient(HttpClient http, URI endpoint, String apiKey, Settings settings) {
        boolean official = endpoint.equals(ENDPOINT);
        boolean local = "http".equals(endpoint.getScheme())
                && Set.of("127.0.0.1", "localhost", "[::1]", "::1").contains(endpoint.getHost())
                && "/v1/responses".equals(endpoint.getPath())
                && endpoint.getRawQuery() == null
                && endpoint.getRawFragment() == null
                && endpoint.getRawUserInfo() == null;
        if (!official && !local) throw new IllegalArgumentException("공식 API 또는 로컬 테스트 주소만 허용합니다.");
        if (http.followRedirects() != HttpClient.Redirect.NEVER)
            throw new IllegalArgumentException("리다이렉트는 허용하지 않습니다.");
        if (apiKey == null || apiKey.isBlank() || apiKey.contains("\r") || apiKey.contains("\n"))
            throw new IllegalArgumentException("API 키 설정을 확인하세요.");
        this.http = http;
        this.endpoint = endpoint;
        this.apiKey = apiKey.trim();
        this.settings = java.util.Objects.requireNonNull(settings);
    }

    static ObjectNode transportSchema(AiCorrectionPrompt prompt) {
        ObjectNode schema = (ObjectNode) JSON.readTree(prompt.responseSchema());
        schema.remove("$schema");
        schema.remove("$id");
        // 전송 Schema만 가공한다. uniqueItems는 기존 v1.1 서버 파서가 검사한다.
        ((ObjectNode) schema.at("/$defs/item/properties/issue_kinds")).remove("uniqueItems");
        return schema;
    }

    static Map<String, Object> body(AiCorrectionPrompt prompt, V11ComparisonInput.Prepared input, Settings settings) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", prompt.model());
        body.put("store", false);
        body.put("stream", false);
        body.put("background", false);
        body.put("service_tier", "default");
        body.put("reasoning", Map.of("effort", settings.reasoningEffort()));
        body.put("max_output_tokens", settings.maxOutputTokens());
        body.put("instructions", prompt.instructions());
        body.put(
                "input",
                List.of(Map.of(
                        "role",
                        "user",
                        "content",
                        List.of(
                                Map.of(
                                        "type",
                                        "input_text",
                                        "text",
                                        "Normalized PNG dimensions: " + input.width() + " x " + input.height()
                                                + " pixels.\n" + prompt.inputText()),
                                Map.of(
                                        "type",
                                        "input_image",
                                        "image_url",
                                        "data:image/png;base64,"
                                                + Base64.getEncoder().encodeToString(input.png()),
                                        "detail",
                                        "high")))));
        body.put(
                "text",
                Map.of(
                        "format",
                        Map.of(
                                "type",
                                "json_schema",
                                "name",
                                "autedi_correction_v1_1",
                                "strict",
                                true,
                                "schema",
                                transportSchema(prompt))));
        return body;
    }

    Reply call(AiCorrectionPrompt prompt, V11ComparisonInput.Prepared input) {
        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(settings.timeout())
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        JSON.writeValueAsString(body(prompt, input, settings)), StandardCharsets.UTF_8))
                .build();
        long start = System.nanoTime();
        var pending = http.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        try {
            HttpResponse<String> response = pending.get(settings.timeout().toMillis(), TimeUnit.MILLISECONDS);
            long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
            String requestId =
                    identifier(response.headers().firstValue("x-request-id").orElse(null));
            if (response.statusCode() != 200) {
                // HTTP 오류 본문은 키·사적인 메시지를 포함할 수 있어 보존하거나 출력하지 않는다.
                return new Reply(
                        elapsed, response.statusCode(), requestId, null, null, "HTTP_" + response.statusCode());
            }
            if (response.body().length() > 4 * 1024 * 1024)
                return new Reply(elapsed, 200, requestId, null, null, "RESPONSE_TOO_LARGE");
            JsonNode envelope;
            try {
                envelope = JSON.readTree(response.body());
                if (!envelope.isObject()) throw new IllegalArgumentException();
            } catch (RuntimeException e) {
                return new Reply(elapsed, 200, requestId, null, null, "INVALID_ENVELOPE");
            }
            StringBuilder text = new StringBuilder();
            String error = envelope.path("status").asString("").equals("completed") ? null : "NOT_COMPLETED";
            if (!envelope.path("output").isArray()) error = "INVALID_OUTPUT";
            for (JsonNode item : envelope.path("output")) {
                if (!"message".equals(item.path("type").asString(""))) continue;
                if (!"completed".equals(item.path("status").asString(""))) error = "INCOMPLETE_MESSAGE";
                if (!item.path("content").isArray()) {
                    error = "INVALID_OUTPUT";
                    continue;
                }
                for (JsonNode content : item.get("content")) {
                    if ("refusal".equals(content.path("type").asString(""))) error = "REFUSAL";
                    if ("output_text".equals(content.path("type").asString(""))) {
                        if (!content.path("text").isString()) error = "INVALID_OUTPUT";
                        else text.append(content.get("text").asString());
                    }
                }
            }
            if (error == null && text.isEmpty()) error = "EMPTY_OUTPUT";
            return new Reply(elapsed, 200, requestId, envelope, text.isEmpty() ? null : text.toString(), error);
        } catch (InterruptedException e) {
            pending.cancel(true);
            Thread.currentThread().interrupt();
            return failure(start, "INTERRUPTED");
        } catch (TimeoutException e) {
            pending.cancel(true);
            return failure(start, "TIMEOUT");
        } catch (java.util.concurrent.ExecutionException e) {
            return failure(
                    start,
                    e.getCause() instanceof java.net.http.HttpTimeoutException ? "TIMEOUT" : "CONNECTION_FAILED");
        }
    }

    private static Reply failure(long start, String code) {
        return new Reply(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start), null, null, null, null, code);
    }

    static String identifier(String value) {
        return value != null && value.matches("[A-Za-z0-9_.:-]{1,128}") ? value : null;
    }
}
