package com.ceos24.autedi.global.sse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.json.JsonMapper;

class SseEventPublisherTest {

    private SseEmitterRegistry registry;
    private SseEventPublisher publisher;

    @BeforeEach
    void setUp() {
        registry = new SseEmitterRegistry(new SseProperties(60_000, 25_000, 5, new SseProperties.Executor(2, 4, 10)));
        publisher = new SseEventPublisher(registry, JsonMapper.builder().build());
    }

    /** 레지스트리가 만든 emitter 대신 mock을 쓰려고 같은 키로 직접 연결을 심는다. */
    private SseEmitter plant(Long userId, SseEmitter emitter) {
        var real = registry.register(userId, Instant.now().plusSeconds(3600));
        // register가 만든 실제 emitter를 mock으로 교체하기 위해 목록을 다시 구성
        registry.remove(userId, real);
        var field = new SseEmitterRegistry.Connection(real.id(), emitter, real.expiresAt(), real.createdAt());
        injectConnection(userId, field);
        return emitter;
    }

    @SuppressWarnings("unchecked")
    private void injectConnection(Long userId, SseEmitterRegistry.Connection connection) {
        try {
            var f = SseEmitterRegistry.class.getDeclaredField("connections");
            f.setAccessible(true);
            var map = (java.util.concurrent.ConcurrentHashMap<
                            Long, java.util.concurrent.CopyOnWriteArrayList<SseEmitterRegistry.Connection>>)
                    f.get(registry);
            map.computeIfAbsent(userId, k -> new java.util.concurrent.CopyOnWriteArrayList<>())
                    .add(connection);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    @DisplayName("대상 사용자의 연결에만 전송하고 다른 사용자에게는 보내지 않는다")
    void onlyTargetUser() throws Exception {
        SseEmitter mine = plant(1L, mock(SseEmitter.class));
        SseEmitter others = plant(2L, mock(SseEmitter.class));

        publisher.publish(1L, "qr.scanned", Map.of("sessionId", "s-1"));

        verify(mine).send(any(SseEmitter.SseEventBuilder.class));
        verify(others, never()).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    @DisplayName("전송 중 IOException이 나면 해당 연결만 정리하고 예외를 전파하지 않는다")
    void sendFailureCleansUp() throws Exception {
        SseEmitter broken = plant(1L, mock(SseEmitter.class));
        SseEmitter healthy = plant(1L, mock(SseEmitter.class));
        doThrow(new IOException("broken pipe")).when(broken).send(any(SseEmitter.SseEventBuilder.class));

        assertThatCode(() -> publisher.publish(1L, "qr.scanned", Map.of("sessionId", "s-1")))
                .doesNotThrowAnyException();

        assertThat(registry.connectionsOf(1L))
                .extracting(SseEmitterRegistry.Connection::emitter)
                .containsExactly(healthy);
        verify(healthy).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    @DisplayName("하트비트 전송에 실패한 연결은 정리된다")
    void heartbeatFailureCleansUp() throws Exception {
        SseEmitter broken = plant(1L, mock(SseEmitter.class));
        doThrow(new IOException("closed")).when(broken).send(any(SseEmitter.SseEventBuilder.class));

        assertThatCode(() -> publisher.heartbeat("ping")).doesNotThrowAnyException();

        assertThat(registry.count(1L)).isZero();
    }

    @Test
    @DisplayName("연결이 없는 사용자에게 발행해도 아무 일도 없다")
    void noConnection() {
        assertThatCode(() -> publisher.publish(99L, "qr.scanned", Map.of())).doesNotThrowAnyException();
    }
}
