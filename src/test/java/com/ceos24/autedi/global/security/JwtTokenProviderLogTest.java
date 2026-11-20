package com.ceos24.autedi.global.security;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class JwtTokenProviderLogTest {

    private final JwtTokenProvider provider =
            new JwtTokenProvider("test-secret-key-for-log-test-0123456789-abcdefghijkl", 3_600_000);
    private Logger logger;
    private Level originalLevel;
    private ListAppender<ILoggingEvent> logs;

    @BeforeEach
    void setUp() {
        logger = (Logger) LoggerFactory.getLogger(JwtTokenProvider.class);
        originalLevel = logger.getLevel();
        logger.setLevel(Level.DEBUG);
        logs = new ListAppender<>();
        logs.start();
        logger.addAppender(logs);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(logs);
        logger.setLevel(originalLevel);
        logs.stop();
    }

    @Test
    @DisplayName("유효하지 않은 토큰은 예외 메시지 원문 없이 예외 종류만 로그에 남긴다")
    void invalidTokenLogsOnlyErrorType() {
        String garbage = "not-a-jwt-secret-value-12345";

        assertThat(provider.validateToken(garbage)).isFalse();

        assertThat(logs.list).hasSize(1);
        ILoggingEvent event = logs.list.get(0);
        assertThat(event.getLevel()).isEqualTo(Level.DEBUG);
        assertThat(event.getFormattedMessage()).isEqualTo("[auth] 유효하지 않은 JWT 토큰: errorType=MalformedJwtException");
        assertThat(event.getFormattedMessage()).doesNotContain(garbage);
        assertThat(event.getThrowableProxy()).isNull();
    }
}
