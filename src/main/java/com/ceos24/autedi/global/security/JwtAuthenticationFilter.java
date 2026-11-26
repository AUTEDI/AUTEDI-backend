package com.ceos24.autedi.global.security;

import com.ceos24.autedi.global.logging.MdcKeys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;
    private final RevokedUserRegistry revokedUserRegistry;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String token = resolveToken(request);

        if (StringUtils.hasText(token) && jwtTokenProvider.validateToken(token)) {
            Authentication authentication = jwtTokenProvider.getAuthentication(token);
            // 퇴사 처리된 사용자의 기존 토큰은 인증하지 않는다. 인증 정보가 없으면 진입점이 401로 응답한다.
            if (!isRevoked(authentication)) {
                SecurityContextHolder.getContext().setAuthentication(authentication);
                putUserInfoToMdc(authentication);
            }
        }

        filterChain.doFilter(request, response);
    }

    private boolean isRevoked(Authentication authentication) {
        return authentication.getPrincipal() instanceof CustomUserPrincipal principal
                && revokedUserRegistry.isRevoked(principal.userId());
    }

    private String resolveToken(HttpServletRequest request) {
        String bearerToken = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith(BEARER_PREFIX)) {
            return bearerToken.substring(BEARER_PREFIX.length());
        }
        return null;
    }

    private void putUserInfoToMdc(Authentication authentication) {
        if (authentication.getPrincipal() instanceof CustomUserPrincipal principal) {
            MDC.put(MdcKeys.USER_ID, String.valueOf(principal.userId()));
            if (principal.academyId() != null) {
                MDC.put(MdcKeys.ACADEMY_ID, String.valueOf(principal.academyId()));
            }
        }
    }
}
