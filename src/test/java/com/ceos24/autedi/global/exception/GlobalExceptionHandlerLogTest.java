package com.ceos24.autedi.global.exception;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.lang.reflect.Method;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.core.MethodParameter;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

class GlobalExceptionHandlerLogTest {

    private static final String SECRET = "secret-value-123";

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
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
    @DisplayName("INFO 코드의 CustomException은 INFO 한 줄이고 예외 메시지는 로그에 없다")
    void customExceptionInfo() {
        handler.handleCustomException(new CustomException(ErrorCode.USER_NOT_FOUND, SECRET));

        assertLogged("INFO", "errorCode=AUTH_006, status=404");
        assertThat(logs.list.get(0).getThrowableProxy()).isNull();
    }

    @Test
    @DisplayName("WARN 코드의 CustomException은 WARN 한 줄이고 스택트레이스가 없다")
    void customExceptionWarn() {
        handler.handleCustomException(new CustomException(ErrorCode.INVALID_IMAGE, SECRET));

        assertLogged("WARN", "errorCode=FILE_004, status=400");
        assertThat(logs.list.get(0).getThrowableProxy()).isNull();
    }

    @Test
    @DisplayName("5xx CustomException은 ERROR이고 원인 예외가 함께 남는다")
    void customExceptionServerErrorKeepsCause() {
        RuntimeException cause = new RuntimeException("s3 down");

        ResponseEntity<?> response =
                handler.handleCustomException(new CustomException(ErrorCode.FILE_STORAGE_FAILED, cause));

        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertLogged("ERROR", "errorCode=FILE_003, status=500");
        assertThat(logs.list.get(0).getThrowableProxy().getCause().getMessage()).isEqualTo("s3 down");
    }

    @Test
    @DisplayName("업로드 크기 초과는 FILE_TOO_LARGE 등급(WARN)으로 남는다")
    void maxUploadSize() {
        handler.handleMaxUploadSizeExceededException(new MaxUploadSizeExceededException(1024));

        assertLogged("WARN", "errorCode=FILE_005, status=413");
    }

    @Test
    @DisplayName("검증 실패 로그에는 거부된 입력 값이 남지 않는다")
    void validationFailureDoesNotLogRejectedValue() throws Exception {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "request");
        bindingResult.addError(new org.springframework.validation.FieldError(
                "request", "password", SECRET, false, null, null, "비밀번호 형식이 올바르지 않습니다."));
        Method method = getClass().getDeclaredMethod("dummy", String.class);
        MethodArgumentNotValidException exception =
                new MethodArgumentNotValidException(new MethodParameter(method, 0), bindingResult);
        // 이 테스트가 의미 있으려면 예외 메시지에 거부된 값이 실제로 들어 있어야 한다
        assertThat(exception.getMessage()).contains(SECRET);

        handler.handleMethodArgumentNotValidException(exception);

        assertLogged("INFO", "errorCode=COMMON_001, status=400");
    }

    @Test
    @DisplayName("요청 본문을 읽지 못한 경우에도 예외 메시지가 로그에 남지 않는다")
    void badRequestDoesNotLogMessage() {
        handler.handleBadRequestException(
                new HttpMessageNotReadableException(SECRET, new MockHttpInputMessage(new byte[0])));

        assertLogged("INFO", "errorCode=COMMON_001, status=400");
    }

    @Test
    @DisplayName("접근 거부는 WARN이고 예외 메시지가 남지 않는다")
    void accessDenied() {
        handler.handleAccessDeniedException(new AccessDeniedException(SECRET));

        assertLogged("WARN", "errorCode=AUTH_002, status=403");
    }

    @Test
    @DisplayName("허용되지 않은 메서드는 WARN이다")
    void methodNotAllowed() {
        handler.handleHttpRequestMethodNotSupportedException(new HttpRequestMethodNotSupportedException("PATCH"));

        assertLogged("WARN", "errorCode=COMMON_002, status=405");
    }

    @Test
    @DisplayName("처리하지 못한 예외는 ERROR이고 예외 객체가 남는다. 응답은 500 그대로")
    void unhandledException() {
        ResponseEntity<?> response = handler.handleException(new IllegalStateException(SECRET));

        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertLogged("ERROR", "errorCode=COMMON_003, status=500");
        assertThat(logs.list.get(0).getThrowableProxy()).isNotNull();
        assertThat(logs.list.get(0).getThrowableProxy().getClassName())
                .isEqualTo(IllegalStateException.class.getName());
    }

    /** 한 줄만 남았는지, 레벨과 내용이 맞는지, 예외 메시지 원문이 로그 문장에 없는지 확인한다. */
    private void assertLogged(String level, String expectedTail) {
        assertThat(logs.list).hasSize(1);
        ILoggingEvent event = logs.list.get(0);
        assertThat(event.getLevel().toString()).isEqualTo(level);
        assertThat(event.getFormattedMessage()).isEqualTo("[api] 요청 실패: " + expectedTail);
        assertThat(event.getFormattedMessage()).doesNotContain(SECRET);
    }

    @SuppressWarnings("unused")
    private void dummy(String value) {}
}
