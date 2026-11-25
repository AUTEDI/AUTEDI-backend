package com.ceos24.autedi.global.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

class JwtAuthenticationFilterRevokedTest {

    private static final String SECRET = "autedi-test-secret-key-must-be-at-least-32-bytes-long-2026";

    private final JwtTokenProvider tokenProvider = new JwtTokenProvider(SECRET, 3_600_000L);
    private final RevokedUserRegistry registry = new RevokedUserRegistry(3_600_000L);
    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(tokenProvider, registry);

    @AfterEach
    void tearDown() {
        MDC.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("차단 목록에 없는 사용자의 유효한 토큰은 인증된다")
    void notRevoked_authenticated() throws Exception {
        String token = tokenProvider.createAccessToken(12L, 3L, "teacher@example.com", "TEACHER");

        AtomicBoolean authenticated = new AtomicBoolean();
        run(token, authenticated);

        assertThat(authenticated).isTrue();
    }

    @Test
    @DisplayName("차단 목록에 있는 사용자의 유효한 토큰은 인증하지 않고, 요청은 다음 필터로 계속 진행된다")
    void revoked_notAuthenticated_butChainContinues() throws Exception {
        String token = tokenProvider.createAccessToken(12L, 3L, "teacher@example.com", "TEACHER");
        registry.revoke(12L);

        AtomicBoolean authenticated = new AtomicBoolean(true);
        AtomicBoolean chainCalled = new AtomicBoolean();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);

        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> {
            chainCalled.set(true);
            authenticated.set(SecurityContextHolder.getContext().getAuthentication() != null);
        });

        assertThat(chainCalled).isTrue();
        assertThat(authenticated).isFalse();
    }

    @Test
    @DisplayName("다른 사용자가 차단돼도 영향이 없다")
    void otherUserRevoked_authenticated() throws Exception {
        String token = tokenProvider.createAccessToken(12L, 3L, "teacher@example.com", "TEACHER");
        registry.revoke(99L);

        AtomicBoolean authenticated = new AtomicBoolean();
        run(token, authenticated);

        assertThat(authenticated).isTrue();
    }

    private void run(String token, AtomicBoolean authenticated) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> authenticated.set(
                SecurityContextHolder.getContext().getAuthentication() != null));
    }
}
