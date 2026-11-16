package com.ceos24.autedi.global.sse;

import java.time.Instant;
import java.util.concurrent.Executor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 주기적으로 하트비트를 보내고, 액세스 토큰이 만료된 연결을 닫는다.
 *
 * <p>스케줄러 스레드는 공용이므로 블로킹 전송은 sseExecutor에 넘긴다.
 */
@Slf4j
@Component
public class SseHeartbeatScheduler {

    private static final String PING_COMMENT = " ping"; // Spring이 ":" 뒤에 그대로 이어 쓰므로 앞 공백을 넣어 ": ping" 형식을 만든다

    private final SseEmitterRegistry registry;
    private final SseEventPublisher publisher;
    private final Executor sseExecutor;

    public SseHeartbeatScheduler(
            SseEmitterRegistry registry, SseEventPublisher publisher, @Qualifier("sseExecutor") Executor sseExecutor) {
        this.registry = registry;
        this.publisher = publisher;
        this.sseExecutor = sseExecutor;
    }

    @Scheduled(fixedDelayString = "${app.sse.heartbeat-interval-ms:25000}")
    public void scheduledHeartbeat() {
        try {
            sseExecutor.execute(this::runOnce);
        } catch (RuntimeException e) {
            log.warn("SSE 하트비트 작업이 거부되어 이번 주기를 건너뜁니다.");
        }
    }

    /** 만료 연결 종료 후 나머지에 ping 전송. 테스트에서 직접 호출할 수 있다. */
    public void runOnce() {
        int expired = registry.closeExpired(Instant.now());
        if (expired > 0) {
            log.debug("토큰 만료로 SSE 연결 {}개 종료", expired);
        }
        publisher.heartbeat(PING_COMMENT);
    }
}
