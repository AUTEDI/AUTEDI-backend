package com.ceos24.autedi.global.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;

class GlobalExceptionHandlerAsyncTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("클라이언트가 먼저 끊은 비동기 응답은 오류 응답을 쓰지 않고 조용히 넘어간다")
    void asyncRequestNotUsableIsIgnored() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThatCode(() -> handler.handleAsyncRequestNotUsable(
                        new AsyncRequestNotUsableException("ServletResponse failed to flushBuffer"), response))
                .doesNotThrowAnyException();

        assertThat(response.getContentAsString()).isEmpty();
        assertThat(response.isCommitted()).isFalse();
    }
}
