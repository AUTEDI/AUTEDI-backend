package com.ceos24.autedi.aiexperiments;

import static com.ceos24.autedi.aiexperiments.V11ComparisonInput.JSON;
import static org.junit.jupiter.api.Assertions.*;

import com.ceos24.autedi.domain.generation.contract.v1_1.prompt.AiCorrectionPromptComposer;
import com.ceos24.autedi.domain.generation.contract.v1_1.validation.AiCorrectionLevel;
import com.ceos24.autedi.support.FakeResponsesServer;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/** loopback 대역만 호출한다. 실제 키·OpenAI API·Spring·DB를 사용하지 않는다. */
class TerraV11ComparisonTest {
    private static final Path MANIFEST = Path.of("src/test/resources/ai/experiments/v1.1/cases.json");
    private static final String KEY = "unit-secret-key";

    @TempDir
    Path temp;

    private FakeResponsesServer server;
    private HttpClient http;

    @BeforeEach
    void setup() throws Exception {
        server = new FakeResponsesServer();
        http = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    @AfterEach
    void cleanup() {
        server.close();
        http.close();
    }

    @Test
    void defaultIsCheckOnlyAndProducesAllSixPlansWithoutNetworkOrCredentials() throws Exception {
        var options = TerraV11Comparison.parse(
                new String[] {"--output", temp.resolve("check").toString()});
        assertFalse(options.live());
        Path result = TerraV11Comparison.run(options, null);
        JsonNode plan = read(result.resolve("plan.json"));
        JsonNode report = read(result.resolve("comparison.json"));
        assertEquals(6, plan.get("plannedCalls").intValue());
        assertEquals(0, report.get("requestAttempts").intValue());
        assertEquals("CHECK_ONLY", report.get("state").asString());
        assertTrue(server.requests().isEmpty());
        assertTrue(plan.get("manifestDescription").asString().contains("합성 인쇄체"));
        for (JsonNode trial : report.get("trials")) {
            Path directory = result.resolve(trial.get("trialId").asString());
            assertFalse(trial.get("requestAttempted").asBoolean());
            assertEquals(
                    "CHECK_ONLY",
                    read(directory.resolve("receipt.json")).get("state").asString());
            assertEquals(
                    "AUTEDI-AI-CONTRACT-v1.1",
                    read(directory.resolve("prompt.json")).get("contractId").asString());
            assertTrue(Files.exists(directory.resolve("transport-schema.json")));
            assertFalse(Files.exists(directory.resolve("envelope.json")));
        }
        String md = Files.readString(result.resolve("comparison.md"));
        assertTrue(md.contains("요청 시도: 0"));
        assertTrue(md.contains("—"));
    }

    @Test
    void levelsUseIdenticalPngTranscriptionSchemaAndSettingsExceptLevelAndKeepRawViolations() throws Exception {
        var options = options("levels", "--run");
        ObjectNode suggestion = (ObjectNode) JSON.readTree(grammar());
        ((ObjectNode) suggestion.at("/items/0")).put("feedback_type", "SUGGESTION");
        server.enqueue(200, withUsage(suggestion.toString()), Map.of("x-request-id", "req_standard"));
        server.enqueue(200, withUsage(grammar()));
        Path result = TerraV11Comparison.run(options, client(options));
        assertEquals(2, server.requests().size());
        JsonNode first = server.requests().get(0).body(),
                second = server.requests().get(1).body();
        assertEquals(first.get("input"), second.get("input"));
        assertEquals(first.get("text"), second.get("text"));
        for (var request : server.requests()) {
            assertEquals("Bearer " + KEY, request.authorization());
            assertEquals("gpt-6.1-terra", request.body().get("model").asString());
            assertEquals("default", request.body().get("service_tier").asString());
            assertEquals("medium", request.body().at("/reasoning/effort").asString());
            assertEquals(12000, request.body().get("max_output_tokens").intValue());
            assertFalse(request.body().get("store").asBoolean());
            assertFalse(request.body().get("stream").asBoolean());
            assertFalse(request.body().get("background").asBoolean());
            assertTrue(request.body().at("/text/format/strict").asBoolean());
            assertEquals(
                    12,
                    request.body().at("/text/format/schema/$defs/item/required").size());
            assertTrue(request.body()
                    .at("/text/format/schema/$defs/item/properties/anchors")
                    .isObject());
            assertTrue(request.body().at("/text/format/schema/$schema").isMissingNode());
            assertTrue(request.body()
                    .at("/text/format/schema/$defs/item/properties/issue_kinds/uniqueItems")
                    .isMissingNode());
        }
        assertTrue(first.get("instructions").asString().contains("AREA GRAMMAR_SPELLING | LEVEL STANDARD"));
        assertTrue(second.get("instructions").asString().contains("AREA GRAMMAR_SPELLING | LEVEL ADVANCED"));
        String url = first.at("/input/0/content/1/image_url").asString();
        assertArrayEquals(
                Files.readAllBytes(result.resolve("grammar-error/image.png")),
                Base64.getDecoder().decode(url.substring("data:image/png;base64,".length())));
        String text = first.at("/input/0/content/0/text").asString();
        JsonNode input = JSON.readTree(text.substring(text.indexOf('\n') + 1));
        assertEquals(
                "She go to school every day.",
                input.at("/transcription_lines/0/text").asString());
        assertEquals("", input.at("/transcription_lines/1/text").asString());
        JsonNode standard = read(result.resolve("grammar-error/r01-standard/result.json"));
        assertEquals("VALIDATED", standard.get("state").asString());
        assertEquals(1, standard.get("rawStandardSuggestionCount").intValue());
        assertEquals(1, standard.at("/rawCounts/suggestions").intValue());
        assertEquals(0, standard.at("/validatedCounts/cards").intValue());
        assertEquals(1, standard.at("/validatedCounts/excluded").intValue());
        assertEquals(1, standard.at("/issueCounts/STANDARD_SUGGESTION").intValue());
        assertEquals(1000, standard.at("/api/inputTokens").intValue());
        assertEquals(300, standard.at("/api/outputTokens").intValue());
        assertEquals(50, standard.at("/api/reasoningTokens").intValue());
        assertEquals(1300, standard.at("/api/totalTokens").intValue());
        assertEquals(120, standard.at("/api/cacheWrite30mTokens").intValue());
        assertEquals(80, standard.at("/api/cacheWrite12hTokens").intValue());
        assertEquals("req_standard", standard.at("/api/requestId").asString());
        assertEquals(suggestion.toString(), Files.readString(result.resolve("grammar-error/r01-standard/output.txt")));
        JsonNode report = read(result.resolve("comparison.json"));
        assertEquals("FINISHED", report.get("state").asString());
        assertEquals(2, report.get("requestAttempts").intValue());
        assertTrue(report.at("/comparisons/0/comparisonAvailable").asBoolean());
        assertEquals("NOT_AUTOMATICALLY_EVALUATED", report.get("qualityVerdict").asString());
        assertEquals(300, report.at("/usageByLabel/STANDARD/outputTokens/total").intValue());
        assertEquals(
                50, report.at("/usageByLabel/STANDARD/reasoningTokens/total").intValue());
        assertTrue(Files.readString(result.resolve("comparison.md")).contains("- [ ] Standard"));
        assertNoSecrets(result);
    }

    @Test
    void promptsModeChangesOnlyVariantAndOrderAlternatesForRepeatedPairs() throws Exception {
        var options = options("prompts", "--run", "--mode", "PROMPTS", "--level", "ADVANCED", "--repeats", "2");
        for (int i = 0; i < 4; i++) server.enqueue(200, FakeResponsesServer.completed("{\"items\":[]}"));
        Path result = TerraV11Comparison.run(options, client(options));
        assertEquals(4, server.requests().size());
        JsonNode first = server.requests().get(0).body();
        for (var request : server.requests()) {
            assertEquals(first.get("input"), request.body().get("input"));
            assertEquals(first.get("text"), request.body().get("text"));
            assertEquals(first.get("model"), request.body().get("model"));
            assertEquals(first.get("reasoning"), request.body().get("reasoning"));
            assertTrue(
                    request.body().get("instructions").asString().contains("AREA GRAMMAR_SPELLING | LEVEL ADVANCED"));
        }
        JsonNode report = read(result.resolve("comparison.json"));
        assertEquals(List.of("DEFAULT", "COMPACT", "COMPACT", "DEFAULT"), labels(report));
        JsonNode full = read(result.resolve("grammar-error/r01-default/prompt.json"));
        JsonNode compact = read(result.resolve("grammar-error/r01-compact/prompt.json"));
        assertEquals(full.get("inputText"), compact.get("inputText"));
        assertEquals(full.get("responseSchema"), compact.get("responseSchema"));
        assertTrue(full.get("instructions").asString().length()
                > compact.get("instructions").asString().length());
        assertTrue(report.at("/trials/0/requestAttempted").asBoolean());
        JsonNode perTrial = read(result.resolve("grammar-error/r01-default/result.json"));
        assertTrue(perTrial.at("/api/inputTokens").isNull());
        assertTrue(perTrial.at("/api/reasoningTokens").isNull());
        assertEquals(
                0, report.at("/usageByLabel/DEFAULT/inputTokens/knownSamples").intValue());
        assertTrue(report.at("/usageByLabel/DEFAULT/inputTokens/total").isNull());
    }

    @Test
    void levelsCanUseCompactAndAlternateTheLevelOrder() throws Exception {
        var options = options("compact-levels", "--run", "--variant", "COMPACT", "--repeats", "2");
        for (int i = 0; i < 4; i++) server.enqueue(200, FakeResponsesServer.completed("{\"items\":[]}"));
        Path result = TerraV11Comparison.run(options, client(options));
        JsonNode report = read(result.resolve("comparison.json"));
        assertEquals(List.of("STANDARD", "ADVANCED", "ADVANCED", "STANDARD"), labels(report));
        report.get("trials")
                .forEach(t -> assertEquals(
                        "AUTEDI-CORRECTION-PROMPT-v1.1-COMPACT",
                        t.get("promptVersion").asString()));
    }

    @Test
    void malformedAiJsonIsSavedAndDoesNotPreventTheOtherComparison() throws Exception {
        var options = options("bad-json", "--run");
        server.enqueue(200, FakeResponsesServer.completed("{not json"));
        server.enqueue(200, FakeResponsesServer.completed("{\"items\":[]}"));
        Path result = TerraV11Comparison.run(options, client(options));
        JsonNode failed = read(result.resolve("grammar-error/r01-standard/result.json"));
        assertEquals("PARSE_FAILED", failed.get("state").asString());
        assertTrue(failed.path("rawCounts").isMissingNode());
        assertEquals("{not json", Files.readString(result.resolve("grammar-error/r01-standard/output.txt")));
        assertEquals(2, server.requests().size());
        assertFalse(read(result.resolve("comparison.json"))
                .at("/comparisons/0/comparisonAvailable")
                .asBoolean());
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 401, 429, 503, 307})
    void httpFailuresAreSanitizedAndStopFollowingCallsWithoutRetry(int status) throws Exception {
        var options = options("http-" + status, "--run");
        server.enqueue(
                status,
                "private-upstream-detail " + KEY,
                Map.of("Location", "https://example.org/never-follow", "x-request-id", "req_failure"));
        Path result = TerraV11Comparison.run(options, client(options));
        assertEquals(1, server.requests().size());
        JsonNode failed = read(result.resolve("grammar-error/r01-standard/result.json"));
        assertEquals("API_FAILED", failed.get("state").asString());
        assertEquals("HTTP_" + status, failed.get("errorCode").asString());
        assertEquals(status, failed.at("/api/httpStatus").intValue());
        assertTrue(failed.at("/api/inputTokens").isNull());
        JsonNode report = read(result.resolve("comparison.json"));
        assertEquals("STOPPED_AFTER_API_FAILURE", report.get("state").asString());
        assertEquals(1, report.get("requestAttempts").intValue());
        assertEquals("NOT_RUN", report.at("/trials/1/state").asString());
        assertFalse(Files.exists(result.resolve("grammar-error/r01-standard/envelope.json")));
        assertNoSecrets(result);
    }

    @ParameterizedTest
    @MethodSource("badEnvelopes")
    void refusesToTreatIncompleteRefusedOrInvalidEnvelopesAsSuccessful(String envelope, String error) throws Exception {
        var options = options("envelope-" + error, "--run");
        server.enqueue(200, envelope);
        Path result = TerraV11Comparison.run(options, client(options));
        assertEquals(1, server.requests().size());
        JsonNode trial = read(result.resolve("grammar-error/r01-standard/result.json"));
        assertEquals("API_FAILED", trial.get("state").asString());
        assertEquals(error, trial.get("errorCode").asString());
    }

    static Stream<Arguments> badEnvelopes() {
        return Stream.of(
                Arguments.of("not json", "INVALID_ENVELOPE"),
                Arguments.of("{\"status\":\"completed\",\"status\":\"incomplete\"}", "INVALID_ENVELOPE"),
                Arguments.of("{\"status\":\"completed\",\"output\":[]}", "EMPTY_OUTPUT"),
                Arguments.of("{\"status\":\"completed\"}", "INVALID_OUTPUT"),
                Arguments.of(
                        FakeResponsesServer.completed("{}")
                                .replace("\"status\":\"completed\"", "\"status\":\"incomplete\""),
                        "INCOMPLETE_MESSAGE"),
                Arguments.of(
                        "{\"status\":\"completed\",\"output\":[{\"type\":\"message\",\"status\":\"completed\",\"content\":[{\"type\":\"refusal\",\"refusal\":\"declined\"}]}]}",
                        "REFUSAL"),
                Arguments.of(
                        "{\"status\":\"completed\",\"output\":[{\"type\":\"message\",\"status\":\"completed\",\"content\":[{\"type\":\"output_text\",\"text\":42}]}]}",
                        "INVALID_OUTPUT"));
    }

    @Test
    void timeoutIncludesAStalledResponseBodyAndPreservesUncertainAttempt() throws Exception {
        var options = options("timeout", "--run", "--timeout-seconds", "1");
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        server.enqueueBlockedBody(FakeResponsesServer.completed("{\"items\":[]}"), entered, release);
        Path result;
        try {
            result = TerraV11Comparison.run(options, client(options));
        } finally {
            release.countDown();
        }
        assertEquals(1, server.requests().size());
        JsonNode trial = read(result.resolve("grammar-error/r01-standard/result.json"));
        assertTrue(trial.get("requestAttempted").asBoolean());
        assertEquals("TIMEOUT", trial.get("errorCode").asString());
        assertTrue(trial.at("/api/httpStatus").isNull());
        assertTrue(trial.at("/api/outputTokens").isNull());
        assertEquals(
                "NOT_RUN",
                read(result.resolve("comparison.json")).at("/trials/1/state").asString());
    }

    @Test
    void outputDirectoryCannotBeReusedIncludingForLiveExecution() throws Exception {
        var check = options("existing");
        TerraV11Comparison.run(check, null);
        var live = options("existing", "--run");
        assertThrows(java.nio.file.FileAlreadyExistsException.class, () -> TerraV11Comparison.run(live, client(live)));
        assertTrue(server.requests().isEmpty());
    }

    @Test
    void sampleInputsAreFrozenAndTransportSchemaDoesNotMutateTheContract() throws Exception {
        var all = V11ComparisonInput.load(MANIFEST, null);
        assertEquals(3, all.size());
        var input = all.getFirst();
        assertEquals(
                List.of("She go to school every day.", ""), input.location().transcriptionLines());
        byte[] changed = input.png();
        changed[0] = 0;
        assertNotEquals(0, input.png()[0]);
        assertEquals(input.imageHash(), V11ComparisonInput.hash(input.png()));
        assertThrows(UnsupportedOperationException.class, () -> input.areas().clear());
        var prompt = new AiCorrectionPromptComposer().compose(input.context(AiCorrectionLevel.STANDARD));
        ObjectNode transport = V11ComparisonClient.transportSchema(prompt);
        assertTrue(JSON.readTree(prompt.responseSchema())
                .at("/$defs/item/properties/issue_kinds/uniqueItems")
                .asBoolean());
        assertTrue(
                transport.at("/$defs/item/properties/issue_kinds/uniqueItems").isMissingNode());
        assertEquals(
                1, transport.at("/$defs/item/properties/issue_kinds/minItems").intValue());
        assertEquals(4, transport.at("/$defs/box/minItems").intValue());
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "--run --check",
                "--mode LEVELS --level STANDARD",
                "--mode PROMPTS --variant COMPACT",
                "--repeats 0",
                "--repeats 6",
                "--unknown value",
                "--mode",
                "--run --run",
                "--max-output-tokens 0",
                "--reasoning invalid",
                "--timeout-seconds 181"
            })
    void rejectsAmbiguousOrInvalidCliSettingsBeforeAnyCall(String args) {
        assertThrows(IllegalArgumentException.class, () -> TerraV11Comparison.parse(args.split(" ")));
        assertTrue(server.requests().isEmpty());
    }

    @Test
    void rejectsUnknownFieldsDuplicatesTraversalIdsAndMissingCases() throws Exception {
        ObjectNode original = (ObjectNode) read(MANIFEST);
        ObjectNode unknown = (ObjectNode) JSON.readTree(original.toString());
        unknown.put("extra", true);
        Path manifest = temp.resolve("invalid.json");
        V11ComparisonReport.writeJson(manifest, unknown);
        assertThrows(IllegalArgumentException.class, () -> V11ComparisonInput.load(manifest, null));
        ObjectNode traversal = (ObjectNode) JSON.readTree(original.toString());
        ((ObjectNode) traversal.at("/cases/0")).put("id", "../outside");
        V11ComparisonReport.writeJson(manifest, traversal);
        assertThrows(IllegalArgumentException.class, () -> V11ComparisonInput.load(manifest, null));
        assertThrows(IllegalArgumentException.class, () -> V11ComparisonInput.load(MANIFEST, "not-present"));
        ObjectNode duplicates = absoluteManifest(2);
        ((ObjectNode) duplicates.at("/cases/1")).put("id", "case0");
        V11ComparisonReport.writeJson(manifest, duplicates);
        assertThrows(IllegalArgumentException.class, () -> V11ComparisonInput.load(manifest, null));
    }

    @Test
    void utf8BomAndLineEndingsAreHandledWithoutTrimmingTheConfirmedText() throws Exception {
        ObjectNode source = absoluteManifest(1);
        Path transcript = temp.resolve("confirmed.txt");
        Files.writeString(transcript, "\uFEFF  She go home.  \r\n\r\n", StandardCharsets.UTF_8);
        ((ObjectNode) source.at("/cases/0")).put("transcription", transcript.toString());
        Path manifest = temp.resolve("bom.json");
        Files.writeString(manifest, "\uFEFF" + source, StandardCharsets.UTF_8);
        var options = TerraV11Comparison.parse(new String[] {
            "--manifest",
            manifest.toString(),
            "--output",
            temp.resolve("bom-result").toString()
        });
        Path result = TerraV11Comparison.run(options, null);
        assertEquals("  She go home.  \n\n", Files.readString(result.resolve("case0/transcription.txt")));
        assertEquals(
                0,
                read(result.resolve("comparison.json")).get("requestAttempts").intValue());
        assertTrue(server.requests().isEmpty());
    }

    @Test
    void rejectsExcessiveCallPlansAndClientSettingsMismatchBeforeCreatingOutput() throws Exception {
        Path manifest = temp.resolve("many.json");
        V11ComparisonReport.writeJson(manifest, absoluteManifest(5));
        var options = TerraV11Comparison.parse(new String[] {
            "--manifest",
            manifest.toString(),
            "--run",
            "--repeats",
            "5",
            "--output",
            temp.resolve("too-many").toString()
        });
        assertThrows(IllegalArgumentException.class, () -> TerraV11Comparison.run(options, client(options)));
        assertFalse(Files.exists(options.output()));
        assertTrue(server.requests().isEmpty());
        var regular = options("mismatch", "--run");
        var changed = options("other", "--run", "--reasoning", "low");
        assertThrows(IllegalArgumentException.class, () -> TerraV11Comparison.run(regular, client(changed)));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "https://example.org/v1/responses",
                "http://api.openai.com/v1/responses",
                "https://api.openai.com/v1/responses?key=x",
                "http://127.0.0.1:9999/other"
            })
    void refusesCredentialForwardingToUnexpectedEndpoints(String endpoint) {
        var options = options("endpoint");
        assertThrows(
                IllegalArgumentException.class,
                () -> new V11ComparisonClient(http, URI.create(endpoint), KEY, options.settings()));
    }

    private TerraV11Comparison.Options options(String name, String... extra) {
        var args = new java.util.ArrayList<>(List.of(
                "--manifest",
                MANIFEST.toString(),
                "--case",
                "grammar-error",
                "--output",
                temp.resolve(name).toString()));
        args.addAll(List.of(extra));
        return TerraV11Comparison.parse(args.toArray(String[]::new));
    }

    private V11ComparisonClient client(TerraV11Comparison.Options options) {
        return new V11ComparisonClient(http, server.endpoint(), KEY, options.settings());
    }

    private static JsonNode read(Path path) throws Exception {
        return JSON.readTree(Files.readString(path, StandardCharsets.UTF_8));
    }

    private static String grammar() throws Exception {
        return Files.readString(
                Path.of("src/test/resources/ai/contract/v1.1/responses/standard-grammar-replace.json"),
                StandardCharsets.UTF_8);
    }

    private static String withUsage(String output) {
        ObjectNode root = (ObjectNode) JSON.readTree(FakeResponsesServer.completed(output));
        root.put("id", "resp_unit");
        root.put("service_tier", "default");
        root.set(
                "usage",
                JSON.valueToTree(Map.of(
                        "input_tokens",
                        1000,
                        "output_tokens",
                        300,
                        "total_tokens",
                        1300,
                        "input_tokens_details",
                        Map.of(
                                "cached_tokens",
                                100,
                                "cache_write_tokens",
                                200,
                                "cache_write_tokens_details",
                                Map.of("ttl_30m_tokens", 120, "ttl_12h_tokens", 80)),
                        "output_tokens_details",
                        Map.of("reasoning_tokens", 50))));
        return root.toString();
    }

    private static List<String> labels(JsonNode report) {
        var values = new java.util.ArrayList<String>();
        report.get("trials").forEach(t -> values.add(t.get("label").asString()));
        return values;
    }

    private static ObjectNode absoluteManifest(int count) throws Exception {
        ObjectNode source = (ObjectNode) read(MANIFEST);
        ArrayNode cases = JSON.createArrayNode();
        for (int i = 0; i < count; i++) {
            ObjectNode value = (ObjectNode) JSON.readTree(source.at("/cases/0").toString());
            value.put("id", "case" + i);
            value.put(
                    "image",
                    MANIFEST.toAbsolutePath()
                            .getParent()
                            .resolve(value.get("image").asString())
                            .toString());
            value.put(
                    "transcription",
                    MANIFEST.toAbsolutePath()
                            .getParent()
                            .resolve(value.get("transcription").asString())
                            .toString());
            cases.add(value);
        }
        source.set("cases", cases);
        return source;
    }

    private static void assertNoSecrets(Path directory) throws Exception {
        try (var files = Files.walk(directory)) {
            for (Path path : files.filter(Files::isRegularFile)
                    .filter(p -> !p.toString().endsWith(".png"))
                    .toList()) {
                String text = Files.readString(path, StandardCharsets.UTF_8);
                assertFalse(text.contains(KEY), path.toString());
                assertFalse(text.contains("private-upstream-detail"), path.toString());
                assertFalse(text.contains("data:image/png;base64,"), path.toString());
            }
        }
    }
}
