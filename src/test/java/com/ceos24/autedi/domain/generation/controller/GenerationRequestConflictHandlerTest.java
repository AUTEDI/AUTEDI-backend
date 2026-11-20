package com.ceos24.autedi.domain.generation.controller;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.ceos24.autedi.global.exception.ApiErrorLogger;
import java.sql.SQLException;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;

class GenerationRequestConflictHandlerTest {

    private final GenerationRequestConflictHandler handler = new GenerationRequestConflictHandler();
    private Logger logger;
    private ListAppender<ILoggingEvent> logs;

    @BeforeEach
    void setUp() {
        logger = (Logger) LoggerFactory.getLogger(ApiErrorLogger.class);
        logs = new ListAppender<>();
        logs.start();
        logger.addAppender(logs);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(logs);
        logs.stop();
    }

    @Test
    @DisplayName("멱등키 중복 제약 위반은 409 응답이고 WARN 한 줄(스택트레이스 없음)이 남는다")
    void idempotencyConflictLogsWarn() {
        DataIntegrityViolationException exception = new DataIntegrityViolationException(
                "duplicate",
                new ConstraintViolationException("dup", new SQLException("dup"), "UK_GENERATION_IDEMPOTENCY_KEY"));

        ResponseEntity<?> response = handler.handle(exception);

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(logs.list).hasSize(1);
        ILoggingEvent event = logs.list.get(0);
        assertThat(event.getLevel().toString()).isEqualTo("WARN");
        assertThat(event.getFormattedMessage()).isEqualTo("[api] 요청 실패: errorCode=GENERATION_007, status=409");
        assertThat(event.getThrowableProxy()).isNull();
    }

    @Test
    @DisplayName("그 외 제약 위반은 500 응답이고 ERROR로 예외가 함께 남는다 (이전에는 로그가 없었다)")
    void otherViolationLogsErrorWithException() {
        DataIntegrityViolationException exception = new DataIntegrityViolationException("not null violation");

        ResponseEntity<?> response = handler.handle(exception);

        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(logs.list).hasSize(1);
        ILoggingEvent event = logs.list.get(0);
        assertThat(event.getLevel().toString()).isEqualTo("ERROR");
        assertThat(event.getFormattedMessage()).isEqualTo("[api] 요청 실패: errorCode=COMMON_003, status=500");
        assertThat(event.getThrowableProxy()).isNotNull();
        assertThat(event.getThrowableProxy().getClassName()).isEqualTo(DataIntegrityViolationException.class.getName());
    }
}
