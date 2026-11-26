package com.ceos24.autedi.global.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RevokedUserRegistryTest {

    private final AtomicLong nanos = new AtomicLong();
    private final RevokedUserRegistry registry = new RevokedUserRegistry(Duration.ofHours(1), nanos::get);

    @Test
    @DisplayName("차단하지 않은 사용자는 차단 목록에 없다")
    void unknownUser_notRevoked() {
        assertThat(registry.isRevoked(1L)).isFalse();
        assertThat(registry.isRevoked(null)).isFalse();
    }

    @Test
    @DisplayName("차단한 사용자는 차단 목록에 있고 다른 사용자는 영향이 없다")
    void revokedUser_isRevoked() {
        registry.revoke(1L);

        assertThat(registry.isRevoked(1L)).isTrue();
        assertThat(registry.isRevoked(2L)).isFalse();
    }

    @Test
    @DisplayName("Access Token 수명(TTL)이 지나면 차단 목록에서 사라진다")
    void revokedUser_expiresAfterTtl() {
        registry.revoke(1L);

        nanos.addAndGet(Duration.ofMinutes(59).toNanos());
        assertThat(registry.isRevoked(1L)).isTrue();

        nanos.addAndGet(Duration.ofMinutes(2).toNanos());
        assertThat(registry.isRevoked(1L)).isFalse();
    }

    @Test
    @DisplayName("트랜잭션이 없으면 revokeAfterCommit은 바로 차단한다")
    void revokeAfterCommit_withoutTransaction_revokesImmediately() {
        registry.revokeAfterCommit(1L);

        assertThat(registry.isRevoked(1L)).isTrue();
    }
}
