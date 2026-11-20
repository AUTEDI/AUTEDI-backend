package com.ceos24.autedi.global.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.servlet.FilterChain;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestIdFilterTest {

    private static final String ID_PATTERN = "[0-9a-f]{8}";

    private final RequestIdFilter filter = new RequestIdFilter();

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    @DisplayName("요청마다 8자리 16진수 requestId를 만들어 응답 헤더에 싣는다")
    void addsRequestIdHeader() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(new MockHttpServletRequest(), response, (req, res) -> {});

        assertThat(response.getHeader(RequestIdFilter.REQUEST_ID_HEADER)).matches(ID_PATTERN);
    }

    @Test
    @DisplayName("서로 다른 요청은 서로 다른 requestId를 받는다")
    void differentRequestsGetDifferentIds() throws Exception {
        MockHttpServletResponse first = new MockHttpServletResponse();
        MockHttpServletResponse second = new MockHttpServletResponse();

        filter.doFilter(new MockHttpServletRequest(), first, (req, res) -> {});
        filter.doFilter(new MockHttpServletRequest(), second, (req, res) -> {});

        assertThat(first.getHeader(RequestIdFilter.REQUEST_ID_HEADER))
                .isNotEqualTo(second.getHeader(RequestIdFilter.REQUEST_ID_HEADER));
    }

    @Test
    @DisplayName("요청을 처리하는 동안 MDC의 requestId는 응답 헤더의 값과 같다")
    void mdcHoldsSameIdWhileProcessing() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> seenInChain = new AtomicReference<>();
        FilterChain chain = (req, res) -> seenInChain.set(MDC.get(MdcKeys.REQUEST_ID));

        filter.doFilter(new MockHttpServletRequest(), response, chain);

        assertThat(seenInChain.get()).isEqualTo(response.getHeader(RequestIdFilter.REQUEST_ID_HEADER));
    }

    @Test
    @DisplayName("요청이 끝나면 체인에서 넣은 userId, academyId까지 MDC가 모두 비워진다")
    void clearsMdcAfterRequest() throws Exception {
        FilterChain chain = (req, res) -> {
            MDC.put(MdcKeys.USER_ID, "12");
            MDC.put(MdcKeys.ACADEMY_ID, "3");
        };

        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), chain);

        assertThat(MDC.get(MdcKeys.REQUEST_ID)).isNull();
        assertThat(MDC.get(MdcKeys.USER_ID)).isNull();
        assertThat(MDC.get(MdcKeys.ACADEMY_ID)).isNull();
    }

    @Test
    @DisplayName("체인에서 예외가 나도 MDC는 비워지고 예외는 그대로 전달된다")
    void clearsMdcEvenWhenChainThrows() {
        FilterChain chain = (req, res) -> {
            MDC.put(MdcKeys.USER_ID, "12");
            throw new IllegalStateException("boom");
        };

        assertThatThrownBy(() -> filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), chain))
                .isInstanceOf(IllegalStateException.class);

        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
    }

    @Test
    @DisplayName("클라이언트가 보낸 X-Request-Id는 무시하고 서버가 새로 만든 값을 쓴다. 요청은 거절하지 않는다")
    void ignoresClientSuppliedRequestId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestIdFilter.REQUEST_ID_HEADER, "client-supplied");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainInvoked = new AtomicBoolean(false);
        AtomicReference<String> seenInChain = new AtomicReference<>();
        FilterChain chain = (req, res) -> {
            chainInvoked.set(true);
            seenInChain.set(MDC.get(MdcKeys.REQUEST_ID));
        };

        filter.doFilter(request, response, chain);

        assertThat(chainInvoked).isTrue();
        assertThat(seenInChain.get()).isNotEqualTo("client-supplied").matches(ID_PATTERN);
        assertThat(response.getHeader(RequestIdFilter.REQUEST_ID_HEADER))
                .isNotEqualTo("client-supplied")
                .matches(ID_PATTERN);
    }
}
