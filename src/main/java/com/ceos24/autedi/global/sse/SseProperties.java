package com.ceos24.autedi.global.sse;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** SSE 연결 관리 설정(app.sse.*). 값이 없으면 기본값을 쓴다. */
@ConfigurationProperties(prefix = "app.sse")
public record SseProperties(
        @DefaultValue("1800000") long emitterTimeoutMs,
        @DefaultValue("25000") long heartbeatIntervalMs,
        @DefaultValue("5") int maxConnectionsPerUser,
        @DefaultValue Executor executor) {

    /** SSE 전송 전용 executor 설정. 첨삭 워커(generationExecutor)와 분리되어 있다. */
    public record Executor(
            @DefaultValue("2") int coreSize, @DefaultValue("4") int maxSize, @DefaultValue("200") int queueCapacity) {}
}
