package com.ceos24.autedi.global.sse;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SseEmitterRegistryTest {

    private SseEmitterRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new SseEmitterRegistry(new SseProperties(60_000, 25_000, 3, new SseProperties.Executor(2, 4, 10)));
    }

    private Instant later() {
        return Instant.now().plusSeconds(3600);
    }

    @Test
    @DisplayName("등록하면 사용자별로 조회되고, 다른 사용자와 섞이지 않는다")
    void register() {
        registry.register(1L, later());
        registry.register(1L, later());
        registry.register(2L, later());

        assertThat(registry.count(1L)).isEqualTo(2);
        assertThat(registry.count(2L)).isEqualTo(1);
        assertThat(registry.totalCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("제거하면 목록에서 빠지고, 마지막 연결이 빠지면 사용자 항목도 사라진다")
    void remove() {
        var c = registry.register(1L, later());

        registry.remove(1L, c);
        registry.remove(1L, c); // 중복 제거는 무해

        assertThat(registry.count(1L)).isZero();
        assertThat(registry.totalCount()).isZero();
        assertThat(registry.snapshotAll()).isEmpty();
    }

    @Test
    @DisplayName("최대 연결 수를 넘으면 가장 오래된 연결부터 닫힌다")
    void evictOldest() {
        var first = registry.register(1L, later());
        var second = registry.register(1L, later());
        var third = registry.register(1L, later());
        var fourth = registry.register(1L, later());

        assertThat(registry.connectionsOf(1L))
                .extracting(SseEmitterRegistry.Connection::id)
                .containsExactly(second.id(), third.id(), fourth.id());
        assertThat(registry.connectionsOf(1L)).doesNotContain(first);
    }

    @Test
    @DisplayName("최대 연결 수는 사용자별로 따로 센다")
    void limitIsPerUser() {
        for (int i = 0; i < 3; i++) {
            registry.register(1L, later());
            registry.register(2L, later());
        }
        assertThat(registry.count(1L)).isEqualTo(3);
        assertThat(registry.count(2L)).isEqualTo(3);
    }

    @Test
    @DisplayName("만료 시각이 지난 연결만 닫히고 만료 전 연결은 유지된다")
    void closeExpired() {
        Instant now = Instant.now();
        var expired = registry.register(1L, now.minusSeconds(1));
        var alive = registry.register(1L, now.plusSeconds(60));
        var otherExpired = registry.register(2L, now);

        int closed = registry.closeExpired(now);

        assertThat(closed).isEqualTo(2);
        assertThat(registry.connectionsOf(1L)).containsExactly(alive);
        assertThat(registry.connectionsOf(1L)).doesNotContain(expired);
        assertThat(registry.count(2L)).isZero();
        assertThat(otherExpired).isNotNull();
    }

    @Test
    @DisplayName("동시에 등록해도 최대 연결 수를 넘지 않는다")
    void concurrentRegister() throws Exception {
        int threads = 16;
        var pool = java.util.concurrent.Executors.newFixedThreadPool(threads);
        var latch = new java.util.concurrent.CountDownLatch(1);
        var futures = new java.util.ArrayList<java.util.concurrent.Future<?>>();
        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                latch.await();
                registry.register(1L, later());
                return null;
            }));
        }
        latch.countDown();
        for (var f : futures) {
            f.get();
        }
        pool.shutdown();

        assertThat(registry.count(1L)).isEqualTo(3);
    }
}
