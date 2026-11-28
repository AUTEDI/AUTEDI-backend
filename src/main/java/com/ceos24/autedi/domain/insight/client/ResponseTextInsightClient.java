package com.ceos24.autedi.domain.insight.client;

import com.ceos24.autedi.domain.insight.service.InsightContract;
import com.ceos24.autedi.global.exception.CustomException;
import com.ceos24.autedi.global.exception.ErrorCode;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 기존 Responses 연동의 텍스트 전용 호출. 입력·응답·시간에 상한이 있고 재시도하지 않는다. */
@Slf4j
public class ResponseTextInsightClient implements InsightClient {
    private static final JsonMapper JSON = JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .build();
    private final HttpClient http;
    private final InsightClientProperties properties;
    private final URI endpoint;
    private final String key;
    private final JsonNode schema;

    public ResponseTextInsightClient(HttpClient http, InsightClientProperties properties, URI endpoint, String key) {
        this.http = http;
        this.properties = properties;
        this.endpoint = endpoint;
        this.key = key;
        try (var stream = ResponseTextInsightClient.class.getResourceAsStream("/ai/insight/v1/response.schema.json")) {
            if (stream == null) throw new IllegalStateException("인사이트 응답 스키마가 필요합니다.");
            schema = JSON.readTree(stream);
        } catch (IOException e) {
            throw new IllegalStateException("인사이트 응답 스키마를 읽을 수 없습니다.", e);
        }
    }

    @Override
    public String generate(String input) {
        if (TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("인사이트 HTTP 대기 중 DB 트랜잭션을 열 수 없습니다.");
        boolean local = endpoint != null
                && endpoint.getHost() != null
                && "http".equals(endpoint.getScheme())
                && List.of("127.0.0.1", "localhost", "[::1]").contains(endpoint.getHost());
        if (endpoint == null
                || endpoint.getHost() == null
                || endpoint.getUserInfo() != null
                || endpoint.getFragment() != null
                || !("https".equals(endpoint.getScheme()) || local)
                || key == null
                || key.isBlank()
                || key.trim().chars().anyMatch(c -> c < 33 || c > 126))
            throw new CustomException(ErrorCode.INSIGHT_NOT_CONFIGURED);
        if (input == null || input.getBytes(StandardCharsets.UTF_8).length > 96 * 1024)
            throw new CustomException(ErrorCode.INSIGHT_INPUT_TOO_LARGE);
        var body = Map.of(
                "model",
                properties.model(),
                "store",
                false,
                "stream",
                false,
                "max_output_tokens",
                properties.maxOutputTokens(),
                "instructions",
                InsightContract.INSTRUCTIONS,
                "input",
                List.of(Map.of("role", "user", "content", List.of(Map.of("type", "input_text", "text", input)))),
                "text",
                Map.of(
                        "format",
                        Map.of(
                                "type",
                                "json_schema",
                                "name",
                                "autedi_student_insight_v1",
                                "strict",
                                true,
                                "schema",
                                schema)));
        var request = HttpRequest.newBuilder(endpoint)
                .timeout(properties.requestTimeout())
                .header("Authorization", "Bearer " + key.trim())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body), StandardCharsets.UTF_8))
                .build();
        long start = System.nanoTime();
        Integer status = null;
        var future = http.sendAsync(request, ignored -> new BoundedBody(256 * 1024));
        try {
            var response = future.get(properties.requestTimeout().toMillis(), TimeUnit.MILLISECONDS);
            status = response.statusCode();
            if (status != 200) throw new CustomException(ErrorCode.INSIGHT_GENERATION_FAILED);
            return output(response.body());
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new CustomException(ErrorCode.INSIGHT_GENERATION_FAILED);
        } catch (InterruptedException e) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new CustomException(ErrorCode.INSIGHT_GENERATION_FAILED);
        } catch (ExecutionException e) {
            throw new CustomException(ErrorCode.INSIGHT_GENERATION_FAILED);
        } finally {
            log.info(
                    "AI_INSIGHT_CALL model={}, elapsedMs={}, httpStatus={}",
                    properties.model(),
                    TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start),
                    status);
        }
    }

    private static String output(byte[] body) {
        try {
            var root = JSON.readTree(body);
            if (!"completed".equals(root.path("status").asString(""))
                    || !root.path("output").isArray()
                    || !root.path("error").isMissingNode()
                            && !root.path("error").isNull()) throw new IllegalArgumentException();
            StringBuilder text = new StringBuilder();
            for (var item : root.get("output")) {
                if (!"message".equals(item.path("type").asString(""))) continue;
                if (!"completed".equals(item.path("status").asString(""))
                        || !"assistant".equals(item.path("role").asString(""))
                        || !item.path("content").isArray()) throw new IllegalArgumentException();
                for (var content : item.get("content")) {
                    if ("refusal".equals(content.path("type").asString(""))) throw new IllegalArgumentException();
                    if ("output_text".equals(content.path("type").asString(""))) {
                        if (!content.path("text").isString()) throw new IllegalArgumentException();
                        text.append(content.get("text").asString());
                    }
                }
            }
            if (text.isEmpty() || text.length() > 16000) throw new IllegalArgumentException();
            return text.toString();
        } catch (RuntimeException e) {
            throw new CustomException(ErrorCode.INSIGHT_INVALID_RESPONSE);
        }
    }

    /** 본문을 전부 할당한 뒤 검사하지 않고 수신 중 상한을 넘으면 구독을 취소한다. */
    private static final class BoundedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final HttpResponse.BodySubscriber<byte[]> delegate = HttpResponse.BodySubscribers.ofByteArray();
        private final int maximum;
        private int received;
        private Flow.Subscription subscription;

        BoundedBody(int maximum) {
            this.maximum = maximum;
        }

        @Override
        public CompletionStage<byte[]> getBody() {
            return delegate.getBody();
        }

        @Override
        public void onSubscribe(Flow.Subscription s) {
            subscription = s;
            delegate.onSubscribe(s);
        }

        @Override
        public void onNext(List<ByteBuffer> buffers) {
            long size = buffers.stream().mapToLong(ByteBuffer::remaining).sum();
            if (size > maximum - received) {
                subscription.cancel();
                delegate.onError(new IOException("insight response limit"));
                return;
            }
            received += (int) size;
            delegate.onNext(buffers);
        }

        @Override
        public void onError(Throwable error) {
            delegate.onError(error);
        }

        @Override
        public void onComplete() {
            delegate.onComplete();
        }
    }
}
