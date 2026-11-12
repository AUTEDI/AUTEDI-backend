package com.ceos24.autedi.global.sse;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * 특정 사용자의 모든 SSE 연결로 이벤트를 전송한다.
 *
 * <p>전송 실패(클라이언트 끊김 등)는 해당 연결을 정리하는 것으로 처리하고 호출자에게 전파하지 않는다. 블로킹 전송이므로 반드시 전용 executor(sseExecutor)
 * 스레드에서 호출해야 한다. payload에는 ID 등 최소 정보만 담고 토큰류는 절대 담지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SseEventPublisher {

    private final SseEmitterRegistry registry;
    private final JsonMapper jsonMapper;

    /** 이벤트를 userId의 모든 연결에 보낸다. 형식: id / event / data({eventId, occurredAt, ...payload}). */
    public void publish(Long userId, String eventName, Map<String, ?> payload) {
        var targets = registry.connectionsOf(userId);
        if (targets.isEmpty()) {
            return;
        }
        String eventId = UUID.randomUUID().toString();
        String data;
        try {
            data = toJson(eventId, payload);
        } catch (JacksonException e) {
            log.warn("SSE 이벤트 직렬화 실패: event={}", eventName);
            return;
        }
        for (SseEmitterRegistry.Connection connection : targets) {
            send(
                    userId,
                    connection,
                    SseEmitter.event().id(eventId).name(eventName).data(data));
        }
    }

    /** 하트비트(주석 줄)를 전체 연결에 보낸다. 실패한 연결은 정리한다. */
    public void heartbeat(String comment) {
        for (SseEmitterRegistry.UserConnection uc : registry.snapshotAll()) {
            send(uc.userId(), uc.connection(), SseEmitter.event().comment(comment));
        }
    }

    private void send(Long userId, SseEmitterRegistry.Connection connection, SseEmitter.SseEventBuilder event) {
        try {
            connection.emitter().send(event);
        } catch (IOException | RuntimeException e) {
            // 클라이언트 끊김 등. 연결만 정리하고 전파하지 않는다.
            log.debug("SSE 전송 실패로 연결 정리: {}", e.getClass().getSimpleName());
            registry.remove(userId, connection);
            try {
                connection.emitter().completeWithError(e);
            } catch (RuntimeException ignored) {
                // 이미 종료된 emitter
            }
        }
    }

    private String toJson(String eventId, Map<String, ?> payload) throws JacksonException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("eventId", eventId);
        body.put(
                "occurredAt",
                LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS).toString());
        if (payload != null) {
            body.putAll(payload);
        }
        return jsonMapper.writeValueAsString(body);
    }
}
