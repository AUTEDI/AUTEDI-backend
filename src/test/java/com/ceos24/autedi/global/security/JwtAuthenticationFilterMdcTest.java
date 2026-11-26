package com.ceos24.autedi.global.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.ceos24.autedi.global.logging.MdcKeys;
import jakarta.servlet.FilterChain;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

class JwtAuthenticationFilterMdcTest {

    private static final String SECRET = "autedi-test-secret-key-must-be-at-least-32-bytes-long-2026";

    private final JwtTokenProvider tokenProvider = new JwtTokenProvider(SECRET, 3_600_000L);
    private final JwtAuthenticationFilter filter =
            new JwtAuthenticationFilter(tokenProvider, new RevokedUserRegistry(3_600_000L));

    @AfterEach
    void tearDown() {
        MDC.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("유효한 토큰이면 요청을 처리하는 동안 userId, academyId가 MDC에 보인다")
    void putsUserAndAcademyIdIntoMdc() throws Exception {
        String token = tokenProvider.createAccessToken(12L, 3L, "teacher@example.com", "TEACHER");
        AtomicReference<String> userId = new AtomicReference<>();
        AtomicReference<String> academyId = new AtomicReference<>();

        run(bearer(token), (req, res) -> {
            userId.set(MDC.get(MdcKeys.USER_ID));
            academyId.set(MDC.get(MdcKeys.ACADEMY_ID));
        });

        assertThat(userId.get()).isEqualTo("12");
        assertThat(academyId.get()).isEqualTo("3");
    }

    @Test
    @DisplayName("이메일은 MDC에 넣지 않는다")
    void doesNotPutEmailIntoMdc() throws Exception {
        String token = tokenProvider.createAccessToken(12L, 3L, "teacher@example.com", "TEACHER");
        AtomicReference<java.util.Map<String, String>> seen = new AtomicReference<>();

        run(bearer(token), (req, res) -> seen.set(MDC.getCopyOfContextMap()));

        assertThat(seen.get()).containsOnlyKeys(MdcKeys.USER_ID, MdcKeys.ACADEMY_ID);
        assertThat(seen.get().values()).noneMatch(value -> value.contains("@"));
    }

    @Test
    @DisplayName("academyId가 없는 토큰이면 academyId는 넣지 않는다 (문자열 \"null\"이 들어가면 안 된다)")
    void skipsAcademyIdWhenAbsent() throws Exception {
        String token = tokenProvider.createAccessToken(5L, null, "admin@example.com", "DIRECTOR");
        AtomicReference<String> userId = new AtomicReference<>();
        AtomicReference<String> academyId = new AtomicReference<>();

        run(bearer(token), (req, res) -> {
            userId.set(MDC.get(MdcKeys.USER_ID));
            academyId.set(MDC.get(MdcKeys.ACADEMY_ID));
        });

        assertThat(userId.get()).isEqualTo("5");
        assertThat(academyId.get()).isNull();
    }

    @Test
    @DisplayName("토큰이 없으면 userId, academyId는 MDC에 없다")
    void noMdcWithoutToken() throws Exception {
        AtomicReference<java.util.Map<String, String>> seen = new AtomicReference<>();

        run(new MockHttpServletRequest(), (req, res) -> seen.set(MDC.getCopyOfContextMap()));

        assertThat(seen.get()).isNullOrEmpty();
    }

    @Test
    @DisplayName("유효하지 않은 토큰이면 userId, academyId는 MDC에 없다")
    void noMdcWithInvalidToken() throws Exception {
        AtomicReference<java.util.Map<String, String>> seen = new AtomicReference<>();

        run(bearer("not-a-valid-token"), (req, res) -> seen.set(MDC.getCopyOfContextMap()));

        assertThat(seen.get()).isNullOrEmpty();
    }

    private MockHttpServletRequest bearer(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        return request;
    }

    private void run(MockHttpServletRequest request, FilterChain chain) throws Exception {
        filter.doFilter(request, new MockHttpServletResponse(), chain);
    }
}
