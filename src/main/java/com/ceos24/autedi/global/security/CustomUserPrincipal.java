package com.ceos24.autedi.global.security;

import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * @param qrSessionId QR 로그인으로 발급된 토큰일 때만 값이 있다(일반 로그인은 null). 업로드 대상 검증 대상 여부를 가른다.
 */
public record CustomUserPrincipal(Long userId, Long academyId, String email, String role, String qrSessionId)
        implements UserDetails {

    public CustomUserPrincipal(Long userId, Long academyId, String email, String role) {
        this(userId, academyId, email, role, null);
    }

    public boolean isQrClient() {
        return qrSessionId != null;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role));
    }

    @Override
    public String getPassword() {
        return null;
    }

    @Override
    public String getUsername() {
        return email;
    }
}
