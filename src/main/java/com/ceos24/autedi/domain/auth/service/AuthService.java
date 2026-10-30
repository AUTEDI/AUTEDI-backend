package com.ceos24.autedi.domain.auth.service;

import com.ceos24.autedi.domain.auth.dto.request.LoginRequest;
import com.ceos24.autedi.domain.auth.dto.request.LogoutRequest;
import com.ceos24.autedi.domain.auth.dto.request.TokenRefreshRequest;
import com.ceos24.autedi.domain.auth.dto.response.LoginResponse;
import com.ceos24.autedi.domain.auth.dto.response.TokenRefreshResponse;
import com.ceos24.autedi.domain.auth.entity.RefreshToken;
import com.ceos24.autedi.domain.auth.repository.RefreshTokenRepository;
import com.ceos24.autedi.domain.user.dto.response.UserResponse;
import com.ceos24.autedi.domain.user.entity.User;
import com.ceos24.autedi.domain.user.entity.UserStatus;
import com.ceos24.autedi.domain.user.repository.UserRepository;
import com.ceos24.autedi.global.exception.CustomException;
import com.ceos24.autedi.global.exception.ErrorCode;
import com.ceos24.autedi.global.security.JwtTokenProvider;
import com.ceos24.autedi.global.util.TokenHashUtil;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    @Value("${jwt.refresh-token-expiration-days:14}")
    private long refreshTokenExpirationDays;

    @Transactional
    public LoginResponse login(LoginRequest request) {
        User user = userRepository
                .findByEmail(request.email())
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_CREDENTIALS));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new CustomException(ErrorCode.INVALID_CREDENTIALS);
        }

        if (user.getStatus() == UserStatus.INACTIVE) {
            throw new CustomException(ErrorCode.USER_INACTIVE);
        }
        if (user.getStatus() == UserStatus.LOCKED) {
            throw new CustomException(ErrorCode.USER_LOCKED);
        }

        LocalDateTime now = LocalDateTime.now();
        user.updateLastLoginAt(now);

        String accessToken = jwtTokenProvider.createAccessToken(
                user.getId(),
                user.getAcademy().getId(),
                user.getEmail(),
                user.getRole().name());

        String rawRefreshToken = TokenHashUtil.generateRawToken();
        String tokenHash = TokenHashUtil.hashToken(rawRefreshToken);

        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .tokenHash(tokenHash)
                .expiresAt(now.plusDays(refreshTokenExpirationDays))
                .build();
        refreshTokenRepository.save(refreshToken);

        return new LoginResponse(accessToken, rawRefreshToken, UserResponse.from(user));
    }

    @Transactional
    public TokenRefreshResponse refresh(TokenRefreshRequest request) {
        String tokenHash = TokenHashUtil.hashToken(request.refreshToken());
        RefreshToken token = refreshTokenRepository
                .findByTokenHash(tokenHash)
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_TOKEN));

        LocalDateTime now = LocalDateTime.now();
        if (!token.isValid(now)) {
            throw new CustomException(ErrorCode.EXPIRED_TOKEN);
        }

        User user = token.getUser();
        if (!user.isActive()) {
            throw new CustomException(ErrorCode.USER_INACTIVE);
        }

        // Refresh Token Rotation: 기존 토큰 폐기 및 새 토큰 발급
        token.revoke(now);

        String newRawRefreshToken = TokenHashUtil.generateRawToken();
        String newTokenHash = TokenHashUtil.hashToken(newRawRefreshToken);

        RefreshToken newRefreshToken = RefreshToken.builder()
                .user(user)
                .tokenHash(newTokenHash)
                .expiresAt(now.plusDays(refreshTokenExpirationDays))
                .build();
        refreshTokenRepository.save(newRefreshToken);

        String newAccessToken = jwtTokenProvider.createAccessToken(
                user.getId(),
                user.getAcademy().getId(),
                user.getEmail(),
                user.getRole().name());

        return new TokenRefreshResponse(newAccessToken, newRawRefreshToken);
    }

    @Transactional
    public void logout(LogoutRequest request) {
        if (request != null && StringUtils.hasText(request.refreshToken())) {
            String tokenHash = TokenHashUtil.hashToken(request.refreshToken());
            refreshTokenRepository.findByTokenHash(tokenHash).ifPresent(token -> token.revoke(LocalDateTime.now()));
        }
    }

    public UserResponse getCurrentUser(Long userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        return UserResponse.from(user);
    }
}
