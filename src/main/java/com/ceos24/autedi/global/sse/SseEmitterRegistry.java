package com.ceos24.autedi.global.sse;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 사용자별 SSE 연결 레지스트리. 단일 인스턴스 전제의 인메모리 구현이다.
 *
 * <p>사용자당 최대 연결 수를 넘으면 가장 오래된 연결을 닫는다. emitter 종료 콜백(onCompletion/onTimeout/onError)에서 반드시 제거한다.
 * 연결 목록은 CopyOnWriteArrayList라 순회 중 제거해도 안전하다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SseEmitterRegistry {

    /** 등록된 연결 하나. expiresAt은 연결 시점 액세스 토큰의 만료 시각이다. */
    public record Connection(String id, SseEmitter emitter, Instant expiresAt, Instant createdAt) {}

    private final SseProperties properties;
    private final ConcurrentHashMap<Long, CopyOnWriteArrayList<Connection>> connections = new ConcurrentHashMap<>();

    /** 새 연결을 등록한다. 최대 연결 수를 넘으면 가장 오래된 연결을 닫는다. */
    public Connection register(Long userId, Instant expiresAt) {
        SseEmitter emitter = new SseEmitter(properties.emitterTimeoutMs());
        Connection connection = new Connection(UUID.randomUUID().toString(), emitter, expiresAt, Instant.now());

        emitter.onCompletion(() -> remove(userId, connection));
        emitter.onTimeout(() -> {
            remove(userId, connection);
            emitter.complete();
        });
        emitter.onError(e -> remove(userId, connection));

        List<Connection> evicted = new ArrayList<>();
        connections.compute(userId, (key, list) -> {
            CopyOnWriteArrayList<Connection> next = list == null ? new CopyOnWriteArrayList<>() : list;
            next.add(connection);
            while (next.size() > properties.maxConnectionsPerUser()) {
                evicted.add(next.remove(0));
            }
            return next;
        });
        // 닫기(complete)는 compute 바깥에서 한다. 콜백이 같은 맵을 다시 건드릴 수 있다.
        evicted.forEach(old -> close(userId, old));
        return connection;
    }

    /** 레지스트리에서만 제거한다. 이미 제거됐으면 아무 일도 하지 않는다. */
    public void remove(Long userId, Connection connection) {
        connections.computeIfPresent(userId, (key, list) -> {
            list.remove(connection);
            return list.isEmpty() ? null : list;
        });
    }

    /** 제거하고 emitter를 정상 종료한다. */
    public void close(Long userId, Connection connection) {
        remove(userId, connection);
        try {
            connection.emitter().complete();
        } catch (RuntimeException e) {
            log.debug("SSE emitter 종료 중 예외 무시: {}", e.getClass().getSimpleName());
        }
    }

    /** 만료 시각이 지난 연결을 모두 닫는다. 닫은 수를 반환한다. */
    public int closeExpired(Instant now) {
        int closed = 0;
        for (var entry : connections.entrySet()) {
            for (Connection connection : entry.getValue()) {
                if (!connection.expiresAt().isAfter(now)) {
                    close(entry.getKey(), connection);
                    closed++;
                }
            }
        }
        return closed;
    }

    public List<Connection> connectionsOf(Long userId) {
        List<Connection> list = connections.get(userId);
        return list == null ? List.of() : List.copyOf(list);
    }

    /** 전체 연결의 (userId, 연결) 스냅샷. 순회 중 제거와 무관하게 안전하다. */
    public List<UserConnection> snapshotAll() {
        List<UserConnection> all = new ArrayList<>();
        connections.forEach((userId, list) -> list.forEach(c -> all.add(new UserConnection(userId, c))));
        return all;
    }

    public int count(Long userId) {
        return connectionsOf(userId).size();
    }

    public int totalCount() {
        return connections.values().stream().mapToInt(List::size).sum();
    }

    public record UserConnection(Long userId, Connection connection) {}
}
