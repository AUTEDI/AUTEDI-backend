package com.ceos24.autedi.global.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 퇴사 처리된 사용자의 이미 발급된 Access Token을 즉시 차단하기 위한 인메모리 차단 목록이다.
 *
 * <p>항목은 Access Token 수명만큼 지나면 자동으로 사라진다. 그 뒤에는 토큰이 이미 만료되어 있기 때문이다. 단일 서버용 구현이라 서버가 여러 대로 늘면
 * 공유 저장소로 바꿔야 한다.
 */
@Component
public class RevokedUserRegistry {

    private final Duration ttl;
    private final Cache<Long, Boolean> revoked;

    @Autowired
    public RevokedUserRegistry(@Value("${jwt.access-token-expiration-ms:3600000}") long accessTokenExpirationMs) {
        this(Duration.ofMillis(accessTokenExpirationMs), Ticker.systemTicker());
    }

    RevokedUserRegistry(Duration ttl, Ticker ticker) {
        this.ttl = ttl;
        this.revoked =
                Caffeine.newBuilder().expireAfterWrite(ttl).ticker(ticker).build();
    }

    /** 즉시 차단 목록에 올린다. */
    public void revoke(Long userId) {
        revoked.put(userId, Boolean.TRUE);
    }

    /**
     * 현재 트랜잭션이 커밋된 뒤에 차단 목록에 올린다. 롤백된 퇴사 처리 때문에 멀쩡한 사용자가 차단되는 일을 막기 위해서다. 트랜잭션이 없으면 바로 올린다.
     */
    public void revokeAfterCommit(Long userId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            revoke(userId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                revoke(userId);
            }
        });
    }

    public boolean isRevoked(Long userId) {
        return userId != null && revoked.getIfPresent(userId) != null;
    }

    public Duration getTtl() {
        return ttl;
    }
}
