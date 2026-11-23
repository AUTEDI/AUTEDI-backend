package com.ceos24.autedi.global.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.slf4j.event.Level;

class ErrorCodeLogLevelTest {

    @ParameterizedTest
    @EnumSource(ErrorCode.class)
    @DisplayName("모든 ErrorCode는 로그 레벨을 가진다")
    void everyErrorCodeHasLogLevel(ErrorCode errorCode) {
        assertThat(errorCode.getLogLevel()).isNotNull();
    }

    @ParameterizedTest
    @EnumSource(ErrorCode.class)
    @DisplayName("5xx 응답이면 ERROR이고, ERROR면 5xx 응답이다")
    void serverErrorIsErrorLevelAndOnlyServerError(ErrorCode errorCode) {
        boolean serverError = errorCode.getStatus().is5xxServerError();

        assertThat(errorCode.getLogLevel() == Level.ERROR).isEqualTo(serverError);
    }

    @ParameterizedTest
    @EnumSource(ErrorCode.class)
    @DisplayName("4xx 응답은 INFO 또는 WARN이다")
    void clientErrorIsInfoOrWarn(ErrorCode errorCode) {
        if (errorCode.getStatus().is4xxClientError()) {
            assertThat(errorCode.getLogLevel()).isIn(Level.INFO, Level.WARN);
        }
    }

    @Test
    @DisplayName("합의한 분류: 표에서 따로 정한 대표 코드의 레벨")
    void agreedClassification() {
        assertThat(ErrorCode.UNAUTHORIZED.getLogLevel()).isEqualTo(Level.INFO);
        assertThat(ErrorCode.ACCESS_DENIED.getLogLevel()).isEqualTo(Level.WARN);
        assertThat(ErrorCode.TOO_MANY_REQUESTS.getLogLevel()).isEqualTo(Level.WARN);
        assertThat(ErrorCode.INVALID_INPUT_VALUE.getLogLevel()).isEqualTo(Level.INFO);
        assertThat(ErrorCode.INVALID_IMAGE.getLogLevel()).isEqualTo(Level.WARN);
        // 표에 이름이 없던 *_NOT_FOUND 중 INFO로 정한 둘
        assertThat(ErrorCode.QR_SESSION_NOT_FOUND.getLogLevel()).isEqualTo(Level.INFO);
        assertThat(ErrorCode.USER_NOT_FOUND.getLogLevel()).isEqualTo(Level.INFO);
        // 나머지 *_NOT_FOUND는 WARN
        assertThat(ErrorCode.WORKSHEET_NOT_FOUND.getLogLevel()).isEqualTo(Level.WARN);
        assertThat(ErrorCode.FILE_NOT_FOUND.getLogLevel()).isEqualTo(Level.WARN);
        assertThat(ErrorCode.FILE_STORAGE_FAILED.getLogLevel()).isEqualTo(Level.ERROR);
    }
}
