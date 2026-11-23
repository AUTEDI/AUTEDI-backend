package com.ceos24.autedi.global.security;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.ceos24.autedi.global.exception.ApiErrorLogger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;

/** GlobalExceptionHandler를 거치지 않고 직접 응답하는 401/403/429가 로그를 남기는지 확인한다. */
class SecurityErrorLogTest {

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
    @DisplayName("401은 INFO 한 줄을 남기고 응답은 그대로다")
    void unauthorizedLogsInfo() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        new CustomAuthenticationEntryPoint()
                .commence(new MockHttpServletRequest(), response, new BadCredentialsException("secret-value-123"));

        assertThat(response.getStatus()).isEqualTo(401);
        assertOneLog("INFO", "errorCode=AUTH_001, status=401");
    }

    @Test
    @DisplayName("403은 WARN 한 줄을 남기고 응답은 그대로다")
    void accessDeniedLogsWarn() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        new CustomAccessDeniedHandler()
                .handle(new MockHttpServletRequest(), response, new AccessDeniedException("secret-value-123"));

        assertThat(response.getStatus()).isEqualTo(403);
        assertOneLog("WARN", "errorCode=AUTH_002, status=403");
    }

    @Test
    @DisplayName("QR rate limit: 허용된 요청은 로그가 없고, 한도를 넘으면 429 응답과 WARN 한 줄이 남는다")
    void rateLimitLogsWarnOnlyWhenBlocked() throws Exception {
        QrRateLimitFilter filter = new QrRateLimitFilter(true, 1, 1, 1, 1, null);

        MockHttpServletResponse allowed = new MockHttpServletResponse();
        filter.doFilter(scanRequest(), allowed, new MockFilterChain());
        assertThat(allowed.getStatus()).isEqualTo(200);
        assertThat(logs.list).isEmpty();

        MockHttpServletResponse blocked = new MockHttpServletResponse();
        filter.doFilter(scanRequest(), blocked, new MockFilterChain());
        assertThat(blocked.getStatus()).isEqualTo(429);
        assertThat(blocked.getHeader("Retry-After")).isNotNull();
        assertOneLog("WARN", "errorCode=COMMON_429, status=429");
    }

    private MockHttpServletRequest scanRequest() {
        return new MockHttpServletRequest("POST", "/api/v1/auth/qr-sessions/scan");
    }

    private void assertOneLog(String level, String expectedTail) {
        assertThat(logs.list).hasSize(1);
        ILoggingEvent event = logs.list.get(0);
        assertThat(event.getLevel().toString()).isEqualTo(level);
        assertThat(event.getFormattedMessage()).isEqualTo("[api] 요청 실패: " + expectedTail);
        assertThat(event.getThrowableProxy()).isNull();
    }
}
