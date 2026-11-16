package com.ceos24.autedi.global.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class JwtTokenProvider {

    private static final String CLAIM_ACADEMY_ID = "academyId";
    private static final String CLAIM_EMAIL = "email";
    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_QR_SESSION_ID = "qrSessionId";

    private final SecretKey secretKey;
    private final long accessTokenExpirationMs;

    public JwtTokenProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-token-expiration-ms:3600000}") long accessTokenExpirationMs) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenExpirationMs = accessTokenExpirationMs;
    }

    public String createAccessToken(Long userId, Long academyId, String email, String role) {
        return createAccessToken(userId, academyId, email, role, null);
    }

    /** qrSessionId가 있으면 QR 로그인으로 발급된 토큰임을 claim으로 표시한다. */
    public String createAccessToken(Long userId, Long academyId, String email, String role, String qrSessionId) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + accessTokenExpirationMs);

        var builder = Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(CLAIM_ACADEMY_ID, academyId)
                .claim(CLAIM_EMAIL, email)
                .claim(CLAIM_ROLE, role);
        if (qrSessionId != null) {
            builder.claim(CLAIM_QR_SESSION_ID, qrSessionId);
        }
        return builder.issuedAt(now).expiration(expiryDate).signWith(secretKey).compact();
    }

    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Invalid JWT token: {}", e.getMessage());
            return false;
        }
    }

    public Authentication getAuthentication(String token) {
        Claims claims = parseClaims(token);
        Long userId = Long.parseLong(claims.getSubject());
        Long academyId = claims.get(CLAIM_ACADEMY_ID, Long.class);
        String email = claims.get(CLAIM_EMAIL, String.class);
        String role = claims.get(CLAIM_ROLE, String.class);

        String qrSessionId = claims.get(CLAIM_QR_SESSION_ID, String.class);

        CustomUserPrincipal principal = new CustomUserPrincipal(userId, academyId, email, role, qrSessionId);
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }

    /** 검증된 액세스 토큰의 만료 시각. */
    public Instant getExpiration(String token) {
        return parseClaims(token).getExpiration().toInstant();
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
