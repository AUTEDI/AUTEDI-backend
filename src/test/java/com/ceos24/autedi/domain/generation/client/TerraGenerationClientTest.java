package com.ceos24.autedi.domain.generation.client;

import static org.junit.jupiter.api.Assertions.*;

import com.ceos24.autedi.domain.generation.contract.v1.AiArea;
import com.ceos24.autedi.domain.generation.contract.v1.prompt.AiCorrectionPromptComposer;
import com.ceos24.autedi.domain.generation.contract.v1.validation.AiCorrectionLevel;
import com.ceos24.autedi.domain.generation.persistence.StoredTranscription;
import com.ceos24.autedi.domain.generation.service.GenerationClientFailure;
import com.ceos24.autedi.domain.worksheet.service.PreparedWorksheetImage;
import com.ceos24.autedi.global.config.TerraHttpConfiguration;
import com.ceos24.autedi.global.storage.LocalStorageService;
import com.ceos24.autedi.support.FakeResponsesServer;
import com.ceos24.autedi.support.TestWorksheetImages;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;

class TerraGenerationClientTest {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private FakeResponsesServer server;
    private TerraClientProperties properties;
    private HttpClient http;
    private TerraGenerationClient client;
    private PreparedWorksheetImage image;

    @BeforeEach
    void setup() throws Exception {
        server = new FakeResponsesServer();
        properties = new TerraClientProperties();
        properties.setEndpoint(server.endpoint());
        properties.setApiKey("test-only-value");
        properties.setRequestTimeout(Duration.ofSeconds(3));
        http = new TerraHttpConfiguration().terraHttpClient(properties);
        client = new TerraGenerationClient(http, properties);
        byte[] png = TestWorksheetImages.png(2, 3);
        image = new PreparedWorksheetImage(1L, 2L, 1, "/test.png", 2, 3, LocalStorageService.sha256(png), png);
    }

    @AfterEach
    void cleanup() {
        server.close();
        http.close();
    }

    @Test
    void twoStepsSendTheSamePngAndThisTranscriptionWithSelectedRulesAndContractV1() {
        server.enqueue(200, FakeResponsesServer.transcription("😀 We go\r\n\r\nhome.\n"));
        server.enqueue(200, FakeResponsesServer.completed("{\"items\":[]}"));
        List<String> lines = client.transcribe(image);
        assertEquals(List.of("😀 We go", "", "home.", ""), lines);
        assertEquals(
                "{\"items\":[]}",
                client.correct(
                        image,
                        frozen(lines),
                        Map.of(
                                AiArea.GRAMMAR_SPELLING,
                                AiCorrectionLevel.STANDARD,
                                AiArea.CONTENT_SUMMARY,
                                AiCorrectionLevel.ADVANCED)));
        assertEquals(2, server.requests().size());
        var first = server.requests().get(0);
        var second = server.requests().get(1);
        for (var request : server.requests()) {
            assertEquals("Bearer test-only-value", request.authorization());
            assertEquals("application/json", request.contentType());
            assertEquals(
                    AiCorrectionPromptComposer.MODEL,
                    request.body().get("model").asString());
            assertFalse(request.body().get("store").asBoolean());
            assertFalse(request.body().get("stream").asBoolean());
            assertFalse(request.body().get("background").asBoolean());
            assertTrue(request.body().at("/text/format/strict").asBoolean());
            String url = request.body().at("/input/0/content/1/image_url").asString();
            assertArrayEquals(
                    image.png(), Base64.getDecoder().decode(url.substring("data:image/png;base64,".length())));
        }
        assertEquals(first.body().at("/input/0/content/1"), second.body().at("/input/0/content/1"));
        assertTrue(first.body().get("instructions").asString().contains("Do not correct grammar"));
        String instructions = second.body().get("instructions").asString();
        assertTrue(instructions.contains("AREA GRAMMAR_SPELLING | LEVEL STANDARD"));
        assertTrue(instructions.contains("AREA CONTENT_SUMMARY | LEVEL ADVANCED"));
        assertFalse(instructions.contains("AREA VOCABULARY_EXPRESSION | LEVEL"));
        String input = second.body().at("/input/0/content/0/text").asString();
        assertTrue(input.startsWith("Normalized PNG dimensions: 2 x 3 pixels.\n"));
        var numbered = JSON.readTree(input.substring(input.indexOf('\n') + 1)).get("transcription_lines");
        assertEquals(4, numbered.size());
        assertEquals("😀 We go", numbered.get(0).get("text").asString());
        assertEquals(2, numbered.get(1).get("line_id").intValue());
        assertEquals("", numbered.get(1).get("text").asString());
        assertEquals("items", second.body().at("/text/format/schema/required/0").asString());
        assertTrue(second.body()
                .at("/text/format/schema/$defs/item/properties/action/enum")
                .toString()
                .contains("GUIDANCE"));
        assertTrue(second.body()
                .at("/text/format/schema/$defs/item/properties/issue_kinds/uniqueItems")
                .isMissingNode());
    }

    @Test
    void emptyDocumentIsAnExplicitNonRetryableFailure() {
        server.enqueue(200, FakeResponsesServer.transcription(""));
        var failure = assertThrows(GenerationClientFailure.class, () -> client.transcribe(image));
        assertEquals("TERRA_NO_TEXT", failure.code());
        assertFalse(failure.retryable());
    }

    @ParameterizedTest
    @CsvSource({
        "401,ignored,TERRA_AUTHENTICATION,false",
        "403,ignored,TERRA_AUTHENTICATION,false",
        "429,insufficient_quota,TERRA_QUOTA,false",
        "429,rate_limit_exceeded,TERRA_RATE_LIMIT,true",
        "503,server_error,TERRA_HTTP_ERROR,true",
        "400,invalid_request_error,TERRA_HTTP_ERROR,false"
    })
    void httpErrorsAreSafeClassifiedAndNeverAutomaticallyRetried(
            int status, String error, String code, boolean retryable) {
        server.enqueue(
                status,
                JSON.writeValueAsString(Map.of("error", Map.of("code", error, "message", "private-upstream-detail"))));
        GenerationClientFailure failure = assertThrows(GenerationClientFailure.class, () -> client.transcribe(image));
        assertEquals(code, failure.code());
        assertEquals(retryable, failure.retryable());
        assertFalse(failure.getMessage().contains("private-upstream-detail"));
        assertNull(failure.getCause());
        assertEquals(1, server.requests().size());
    }

    @ParameterizedTest
    @CsvSource({
        "incomplete,max_output_tokens,TERRA_INCOMPLETE,true",
        "incomplete,content_filter,TERRA_CONTENT_FILTER,false",
        "in_progress,none,TERRA_NOT_COMPLETED,true"
    })
    void unfinishedEnvelopesAreRejectedEvenWhenTheyContainValidJson(
            String status, String reason, String code, boolean retryable) {
        var envelope =
                (tools.jackson.databind.node.ObjectNode) JSON.readTree(FakeResponsesServer.transcription("partial"));
        envelope.put("status", status);
        envelope.set("incomplete_details", JSON.valueToTree(Map.of("reason", reason)));
        server.enqueue(200, envelope.toString());
        GenerationClientFailure failure = assertThrows(GenerationClientFailure.class, () -> client.transcribe(image));
        assertEquals(code, failure.code());
        assertEquals(retryable, failure.retryable());
        assertEquals(1, server.requests().size());
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "not-json",
                "{\"status\":\"completed\",\"output\":[]}",
                "{\"status\":\"completed\",\"output\":[{\"type\":\"message\",\"role\":\"assistant\",\"status\":\"completed\",\"content\":[{\"type\":\"refusal\",\"refusal\":\"private-detail\"}]}]}"
            })
    void malformedEmptyAndRefusedOutputsNeverBecomeATranscription(String body) {
        server.enqueue(200, body);
        var failure = assertThrows(GenerationClientFailure.class, () -> client.transcribe(image));
        String expected = body.equals("not-json")
                ? "TERRA_INVALID_RESPONSE"
                : body.contains("refusal") ? "TERRA_REFUSAL" : "TERRA_EMPTY_OUTPUT";
        assertEquals(expected, failure.code());
        assertFalse(failure.getMessage().contains("private-detail"));
    }

    @Test
    void malformedTranscriptionAndIncompleteMessageAreRejected() {
        server.enqueue(200, FakeResponsesServer.completed("{\"text\":123}"));
        assertEquals(
                "TERRA_INVALID_RESPONSE",
                assertThrows(GenerationClientFailure.class, () -> client.transcribe(image))
                        .code());
        var envelope = JSON.readTree(FakeResponsesServer.transcription("partial"));
        ((tools.jackson.databind.node.ObjectNode) envelope.at("/output/1")).put("status", "incomplete");
        server.enqueue(200, envelope.toString());
        assertEquals(
                "TERRA_INCOMPLETE",
                assertThrows(GenerationClientFailure.class, () -> client.transcribe(image))
                        .code());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void timeoutDoesNotRetryTheRequestEvenIfHeadersHaveArrived(boolean headersFirst) throws Exception {
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        properties.setRequestTimeout(Duration.ofMillis(500));
        if (headersFirst) server.enqueueBlockedBody(FakeResponsesServer.transcription("late"), entered, release);
        else server.enqueueBlocked(FakeResponsesServer.transcription("late"), entered, release);
        try {
            var failure = assertThrows(GenerationClientFailure.class, () -> client.transcribe(image));
            assertEquals("TERRA_TIMEOUT", failure.code());
            assertTrue(failure.retryable());
            assertTrue(entered.await(1, TimeUnit.SECONDS));
            assertEquals(1, server.requests().size());
        } finally {
            release.countDown();
        }
    }

    @Test
    void missingKeyAndMismatchedTranscriptionDoNotSendRequestsAndRedirectsAreNotFollowed() {
        properties.setApiKey("");
        assertEquals(
                "TERRA_NOT_CONFIGURED",
                assertThrows(GenerationClientFailure.class, () -> client.transcribe(image))
                        .code());
        assertEquals(0, server.requests().size());
        properties.setApiKey("test-only-value");
        var other = new PreparedWorksheetImage(9L, 8L, 1, "/other.png", 2, 3, image.sha256(), image.png());
        assertEquals(
                "TERRA_INPUT_MISMATCH",
                assertThrows(
                                GenerationClientFailure.class,
                                () -> client.correct(other, frozen(List.of("text")), Map.of()))
                        .code());
        assertEquals(0, server.requests().size());
        server.enqueue(302, "{}", Map.of("Location", server.endpoint().toString()));
        var redirect = assertThrows(GenerationClientFailure.class, () -> client.transcribe(image));
        assertEquals("TERRA_HTTP_ERROR", redirect.code());
        assertFalse(redirect.retryable());
        assertEquals(1, server.requests().size());
    }

    private StoredTranscription frozen(List<String> lines) {
        return new StoredTranscription(
                3L,
                4L,
                1L,
                2L,
                1,
                "file-hash",
                "/image.png",
                image.sha256(),
                2,
                3,
                AiCorrectionPromptComposer.MODEL,
                "LF-PRESERVE-v1",
                LocalStorageService.sha256(String.join("\n", lines).getBytes(StandardCharsets.UTF_8)),
                lines);
    }
}
