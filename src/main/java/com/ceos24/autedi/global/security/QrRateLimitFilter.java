package com.ceos24.autedi.global.security;

import com.ceos24.autedi.global.exception.ErrorCode;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * QR 로그인 비인증 엔드포인트(scan, status, token)의 클라이언트 IP별 rate limit 필터.
 *
 * <p>Bucket4j 토큰 버킷을 인메모리(Caffeine, 미사용 10분 후 제거)로 관리하는 단일 인스턴스용 구현이다. 요청 바디(qrToken)는 읽지 않으며 로그에도
 * 남기지 않는다.
 */
public class QrRateLimitFilter extends OncePerRequestFilter {

    private static final String BASE = "/api/v1/auth/qr-sessions";

    private final boolean enabled;
    private final String clientIpHeader;
    private final Map<String, Integer> limitsPerMinute;
    private final Cache<String, Bucket> buckets = Caffeine.newBuilder()
            .expireAfterAccess(Duration.ofMinutes(10))
            .maximumSize(100_000)
            .build();

    public QrRateLimitFilter(
            boolean enabled, int scanPerMinute, int tokenPerMinute, int statusPerMinute, String clientIpHeader) {
        this.enabled = enabled;
        this.clientIpHeader = clientIpHeader;
        this.limitsPerMinute = Map.of(
                BASE + "/scan", scanPerMinute,
                BASE + "/token", tokenPerMinute,
                BASE + "/status", statusPerMinute);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !enabled
                || !HttpMethod.POST.matches(request.getMethod())
                || !limitsPerMinute.containsKey(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        int limit = limitsPerMinute.get(path);
        Bucket bucket = buckets.get(clientIp(request) + "|" + path, key -> newBucket(limit));

        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        if (probe.isConsumed()) {
            chain.doFilter(request, response);
            return;
        }

        long retryAfterSeconds =
                Math.max(1, Duration.ofNanos(probe.getNanosToWaitForRefill()).toSeconds());
        ErrorCode errorCode = ErrorCode.TOO_MANY_REQUESTS;
        response.setStatus(errorCode.getStatus().value());
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter()
                .write("{\"isSuccess\":false,\"code\":\"%s\",\"message\":\"%s\",\"result\":null,\"error\":null}"
                        .formatted(errorCode.getCode(), errorCode.getMessage()));
    }

    private Bucket newBucket(int limitPerMinute) {
        return Bucket.builder()
                .addLimit(limit -> limit.capacity(limitPerMinute).refillGreedy(limitPerMinute, Duration.ofMinutes(1)))
                .build();
    }

    private String clientIp(HttpServletRequest request) {
        if (StringUtils.hasText(clientIpHeader)) {
            String value = request.getHeader(clientIpHeader);
            if (StringUtils.hasText(value)) {
                // 신뢰하는 프록시가 덧붙인 가장 오른쪽 값만 사용한다 (왼쪽 값은 클라이언트가 위조 가능).
                String[] parts = value.split(",");
                return parts[parts.length - 1].trim();
            }
        }
        return request.getRemoteAddr();
    }
}
