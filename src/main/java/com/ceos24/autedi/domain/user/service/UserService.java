package com.ceos24.autedi.domain.user.service;

import com.ceos24.autedi.domain.auth.repository.RefreshTokenRepository;
import com.ceos24.autedi.domain.user.dto.request.PasswordChangeRequest;
import com.ceos24.autedi.domain.user.dto.request.UserUpdateRequest;
import com.ceos24.autedi.domain.user.dto.response.UserResponse;
import com.ceos24.autedi.domain.user.entity.User;
import com.ceos24.autedi.domain.user.repository.UserRepository;
import com.ceos24.autedi.global.exception.CustomException;
import com.ceos24.autedi.global.exception.ErrorCode;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;

    public UserResponse getCurrentUser(Long userId) {
        return UserResponse.from(getUser(userId));
    }

    /** 로그인한 본인의 이름을 바꾼다. 앞뒤 공백은 제거해서 저장한다. */
    @Transactional
    public UserResponse updateName(Long userId, UserUpdateRequest request) {
        User user = getUser(userId);
        user.updateName(request.name().trim());
        return UserResponse.from(user);
    }

    /**
     * 현재 비밀번호를 확인한 뒤 새 비밀번호로 바꾸고, 이 사용자의 모든 Refresh Token을 폐기한다. 이미 발급된 Access Token은 만료될 때까지 유효하다.
     */
    @Transactional
    public void changePassword(Long userId, PasswordChangeRequest request) {
        User user = getUser(userId);

        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new CustomException(ErrorCode.CURRENT_PASSWORD_MISMATCH);
        }
        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new CustomException(ErrorCode.SAME_AS_CURRENT_PASSWORD);
        }

        user.changePassword(passwordEncoder.encode(request.newPassword()));
        refreshTokenRepository.revokeAllByUser(user, LocalDateTime.now());
    }

    private User getUser(Long userId) {
        return userRepository.findById(userId).orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
    }
}
