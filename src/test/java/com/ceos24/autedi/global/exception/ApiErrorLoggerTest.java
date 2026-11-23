package com.ceos24.autedi.global.exception;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.slf4j.LoggerFactory;

class ApiErrorLoggerTest {

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

    @ParameterizedTest
    @EnumSource(ErrorCode.class)
    @DisplayName("ErrorCode에 정해 둔 레벨로 한 줄을 남긴다")
    void logsAtLevelOfErrorCode(ErrorCode errorCode) {
        ApiErrorLogger.log(errorCode);

        assertThat(logs.list).hasSize(1);
        assertThat(logs.list.get(0).getLevel().toString())
                .isEqualTo(errorCode.getLogLevel().name());
    }

    @Test
    @DisplayName("메시지 형식: [api] 요청 실패: errorCode=코드, status=상태값")
    void messageFormat() {
        ApiErrorLogger.log(ErrorCode.FILE_STORAGE_FAILED);

        assertThat(logs.list.get(0).getFormattedMessage()).isEqualTo("[api] 요청 실패: errorCode=FILE_003, status=500");
    }

    @Test
    @DisplayName("4xx는 원인 예외를 넘겨도 스택트레이스를 붙이지 않는다")
    void clientErrorHasNoStackTrace() {
        ApiErrorLogger.log(ErrorCode.INVALID_IMAGE, new IllegalStateException("boom"));

        assertThat(logs.list).hasSize(1);
        assertThat(logs.list.get(0).getThrowableProxy()).isNull();
    }

    @Test
    @DisplayName("5xx는 예외를 붙여서 스택트레이스와 원인(cause)이 함께 남는다")
    void serverErrorKeepsExceptionAndCause() {
        RuntimeException s3Failure = new RuntimeException("s3 down");
        CustomException wrapped = new CustomException(ErrorCode.FILE_STORAGE_FAILED, s3Failure);

        ApiErrorLogger.log(ErrorCode.FILE_STORAGE_FAILED, wrapped);

        ILoggingEvent event = logs.list.get(0);
        assertThat(event.getLevel().toString()).isEqualTo("ERROR");
        assertThat(event.getThrowableProxy()).isNotNull();
        assertThat(event.getThrowableProxy().getClassName()).isEqualTo(CustomException.class.getName());
        assertThat(event.getThrowableProxy().getCause()).isNotNull();
        assertThat(event.getThrowableProxy().getCause().getMessage()).isEqualTo("s3 down");
    }

    @Test
    @DisplayName("예외 없이 5xx를 남기면 예외 없이 한 줄만 남는다")
    void serverErrorWithoutExceptionIsOneLine() {
        ApiErrorLogger.log(ErrorCode.AI_CONTRACT_NOT_READY);

        assertThat(logs.list).hasSize(1);
        assertThat(logs.list.get(0).getLevel().toString()).isEqualTo("ERROR");
        assertThat(logs.list.get(0).getThrowableProxy()).isNull();
    }

    @Test
    @DisplayName("예외 메시지 원문은 로그 메시지에 들어가지 않는다")
    void exceptionMessageIsNotInLogText() {
        ApiErrorLogger.log(ErrorCode.INVALID_INPUT_VALUE, new IllegalArgumentException("secret-value-123"));

        assertThat(logs.list.get(0).getFormattedMessage()).doesNotContain("secret-value-123");
    }
}
