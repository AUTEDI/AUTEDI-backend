package com.ceos24.autedi.support;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 외부 주소에 접근하지 않는 loopback 전용 Responses API 대역. */
public final class FakeResponsesServer implements AutoCloseable {
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private record Reply(
            int status,
            String body,
            Map<String, String> headers,
            CountDownLatch entered,
            CountDownLatch release,
            boolean headersFirst) {}

    public record Request(JsonNode body, String authorization, String contentType) {}

    private final HttpServer server;
    private final ExecutorService threads = Executors.newCachedThreadPool();
    private final BlockingQueue<Reply> replies = new LinkedBlockingQueue<>();
    private final List<Request> requests = new CopyOnWriteArrayList<>();

    public FakeResponsesServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(threads);
        server.createContext("/v1/responses", exchange -> {
            try {
                requests.add(new Request(
                        JSON.readTree(exchange.getRequestBody().readAllBytes()),
                        exchange.getRequestHeaders().getFirst("Authorization"),
                        exchange.getRequestHeaders().getFirst("Content-Type")));
                Reply reply = replies.poll(2, TimeUnit.SECONDS);
                if (reply == null) reply = new Reply(503, "{}", Map.of(), null, null, false);
                byte[] body = reply.body().getBytes(StandardCharsets.UTF_8);
                if (reply.headersFirst()) exchange.sendResponseHeaders(reply.status(), body.length);
                if (reply.entered() != null) reply.entered().countDown();
                if (reply.release() != null) reply.release().await(5, TimeUnit.SECONDS);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                reply.headers()
                        .forEach((key, value) -> exchange.getResponseHeaders().set(key, value));
                if (!reply.headersFirst()) exchange.sendResponseHeaders(reply.status(), body.length);
                exchange.getResponseBody().write(body);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (IOException ignored) {
                // 타임아웃·취소 테스트에서 클라이언트가 먼저 연결을 닫을 수 있다.
            } finally {
                exchange.close();
            }
        });
        server.start();
    }

    public URI endpoint() {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/v1/responses");
    }

    public List<Request> requests() {
        return List.copyOf(requests);
    }

    public void enqueue(int status, String body) {
        enqueue(status, body, Map.of());
    }

    public void enqueue(int status, String body, Map<String, String> headers) {
        replies.add(new Reply(status, body, headers, null, null, false));
    }

    public void enqueueBlocked(String body, CountDownLatch entered, CountDownLatch release) {
        replies.add(new Reply(200, body, Map.of(), entered, release, false));
    }

    public void enqueueBlockedBody(String body, CountDownLatch entered, CountDownLatch release) {
        replies.add(new Reply(200, body, Map.of(), entered, release, true));
    }

    public static String completed(String output) {
        return JSON.writeValueAsString(Map.of(
                "status",
                "completed",
                "model",
                "gpt-6.1-terra",
                "output",
                List.of(
                        Map.of("type", "reasoning", "summary", List.of()),
                        Map.of(
                                "type",
                                "message",
                                "role",
                                "assistant",
                                "status",
                                "completed",
                                "content",
                                List.of(Map.of("type", "output_text", "text", output))))));
    }

    public static String transcription(String text) {
        return completed(JSON.writeValueAsString(Map.of("text", text)));
    }

    @Override
    public void close() {
        server.stop(0);
        threads.shutdownNow();
    }
}
