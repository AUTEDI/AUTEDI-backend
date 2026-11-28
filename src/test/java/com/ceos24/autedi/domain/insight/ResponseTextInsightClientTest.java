package com.ceos24.autedi.domain.insight;

import static org.assertj.core.api.Assertions.*;

import com.ceos24.autedi.domain.insight.client.*;
import com.ceos24.autedi.global.exception.*;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class ResponseTextInsightClientTest {
    private final JsonMapper json = JsonMapper.builder().build();
    private HttpServer server;
    private ExecutorService executor;
    private HttpClient http;
    private URI endpoint;
    private final AtomicInteger calls = new AtomicInteger();
    private volatile int httpStatus = 200;
    private volatile String body;
    private volatile JsonNode request;
    private volatile boolean delayHeaders, delayBody;

    @BeforeEach
    void setup() throws Exception {
        calls.set(0);
        body = envelope("{\"insights\":[],\"coachingPoints\":[]}");
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        executor = Executors.newCachedThreadPool();
        server.setExecutor(executor);
        server.createContext("/responses", exchange -> {
            calls.incrementAndGet();
            try {
                request = json.readTree(exchange.getRequestBody().readAllBytes());
                if (delayHeaders) Thread.sleep(400);
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(httpStatus, bytes.length);
                if (delayBody) {
                    exchange.getResponseBody().write(bytes, 0, 1);
                    exchange.getResponseBody().flush();
                    Thread.sleep(400);
                    exchange.getResponseBody().write(bytes, 1, bytes.length - 1);
                } else exchange.getResponseBody().write(bytes);
            } catch (Exception ignored) {
            } finally {
                exchange.close();
            }
        });
        server.start();
        endpoint = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/responses");
        http = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(1))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    @AfterEach
    void cleanup() {
        server.stop(0);
        executor.shutdownNow();
        http.shutdownNow();
    }

    @Test
    void sendsOnlyTextWithStrictSchemaAndOutputLimit() {
        String output = client(Duration.ofSeconds(2), "test-only-key").generate("PRIVATE_FEEDBACK");
        assertThat(output).contains("coachingPoints");
        assertThat(calls.get()).isEqualTo(1);
        assertThat(request.path("input").get(0).path("content").size()).isEqualTo(1);
        assertThat(request.at("/input/0/content/0/type").asText()).isEqualTo("input_text");
        assertThat(request.at("/input/0/content/0/text").asText()).isEqualTo("PRIVATE_FEEDBACK");
        assertThat(request.at("/text/format/strict").asBoolean()).isTrue();
        assertThat(request.path("max_output_tokens").asInt()).isEqualTo(600);
        assertThat(request.path("store").asBoolean()).isFalse();
        assertThat(request.toString()).doesNotContain("input_image", "base64", "test-only-key");
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 403, 429, 500, 503})
    void providerFailureIsNotRetriedOrReturnedVerbatim(int code) {
        httpStatus = code;
        body = "PRIVATE_PROVIDER_MESSAGE";
        assertThatThrownBy(() -> client(Duration.ofSeconds(2), "test-only-key").generate("data"))
                .isInstanceOfSatisfying(CustomException.class, e -> {
                    assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INSIGHT_GENERATION_FAILED);
                    assertThat(e.getMessage()).doesNotContain("PRIVATE_PROVIDER_MESSAGE");
                });
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void rejectsMissingConfigurationAndTooLargeInputBeforeHttp() {
        assertThatThrownBy(() -> client(Duration.ofSeconds(2), "").generate("data"))
                .isInstanceOfSatisfying(CustomException.class, e -> assertThat(e.getErrorCode())
                        .isEqualTo(ErrorCode.INSIGHT_NOT_CONFIGURED));
        assertThatThrownBy(() -> client(Duration.ofSeconds(2), "key").generate("x".repeat(96 * 1024 + 1)))
                .isInstanceOfSatisfying(CustomException.class, e -> assertThat(e.getErrorCode())
                        .isEqualTo(ErrorCode.INSIGHT_INPUT_TOO_LARGE));
        assertThat(calls.get()).isZero();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "http:broken",
                "http://example.com/responses",
                "https://user:pass@example.com/responses",
                "https://example.com/path#fragment"
            })
    void invalidEndpointIsAConfigurationErrorWithoutSendingCredentials(String address) {
        var invalid = new ResponseTextInsightClient(
                http,
                new InsightClientProperties("test-model", Duration.ofSeconds(2), 600),
                URI.create(address),
                "key");
        assertThatThrownBy(() -> invalid.generate("data"))
                .isInstanceOfSatisfying(CustomException.class, e -> assertThat(e.getErrorCode())
                        .isEqualTo(ErrorCode.INSIGHT_NOT_CONFIGURED));
        assertThat(calls.get()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"bad\nvalue", "bad\rvalue", "bad\000value"})
    void invalidHeaderKeyIsRejectedBeforeRequestConstruction(String key) {
        assertThatThrownBy(() -> client(Duration.ofSeconds(2), key).generate("data"))
                .isInstanceOfSatisfying(CustomException.class, e -> assertThat(e.getErrorCode())
                        .isEqualTo(ErrorCode.INSIGHT_NOT_CONFIGURED));
        assertThat(calls.get()).isZero();
    }

    @Test
    void rejectsOpenDatabaseTransactionBeforeHttp() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            assertThatThrownBy(() -> client(Duration.ofSeconds(2), "key").generate("data"))
                    .isInstanceOf(IllegalStateException.class);
        } finally {
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
        assertThat(calls.get()).isZero();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void timeoutCoversHeadersAndStalledResponseBody(boolean headers) {
        delayHeaders = headers;
        delayBody = !headers;
        assertThatThrownBy(() -> client(Duration.ofMillis(100), "key").generate("data"))
                .isInstanceOfSatisfying(CustomException.class, e -> assertThat(e.getErrorCode())
                        .isEqualTo(ErrorCode.INSIGHT_GENERATION_FAILED));
    }

    @Test
    void capsResponseWhileReceivingInsteadOfBufferingUnlimitedBody() {
        body = "x".repeat(300 * 1024);
        assertThatThrownBy(() -> client(Duration.ofSeconds(2), "key").generate("data"))
                .isInstanceOfSatisfying(CustomException.class, e -> assertThat(e.getErrorCode())
                        .isEqualTo(ErrorCode.INSIGHT_GENERATION_FAILED));
        assertThat(calls.get()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{}",
                "[]",
                "not-json",
                "{\"status\":\"incomplete\",\"output\":[]}",
                "{\"status\":\"completed\",\"output\":[{\"type\":\"message\",\"status\":\"completed\",\"role\":\"assistant\",\"content\":[{\"type\":\"refusal\"}]}]}"
            })
    void rejectsUnreadableIncompleteAndRefusedResponse(String raw) {
        body = raw;
        assertThatThrownBy(() -> client(Duration.ofSeconds(2), "key").generate("data"))
                .isInstanceOfSatisfying(CustomException.class, e -> assertThat(e.getErrorCode())
                        .isEqualTo(ErrorCode.INSIGHT_INVALID_RESPONSE));
    }

    private ResponseTextInsightClient client(Duration timeout, String key) {
        return new ResponseTextInsightClient(
                http, new InsightClientProperties("test-model", timeout, 600), endpoint, key);
    }

    private String envelope(String output) {
        return json.writeValueAsString(Map.of(
                "status",
                "completed",
                "output",
                List.of(Map.of(
                        "type",
                        "message",
                        "status",
                        "completed",
                        "role",
                        "assistant",
                        "content",
                        List.of(Map.of("type", "output_text", "text", output))))));
    }
}
