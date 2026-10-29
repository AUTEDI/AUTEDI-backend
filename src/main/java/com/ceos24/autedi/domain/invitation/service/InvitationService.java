package com.ceos24.autedi.domain.invitation.service;

import com.ceos24.autedi.domain.academy.entity.Academy;
import com.ceos24.autedi.domain.auth.dto.request.SignupRequest;
import com.ceos24.autedi.domain.auth.dto.response.SignupResponse;
import com.ceos24.autedi.domain.invitation.dto.request.InvitationCreateRequest;
import com.ceos24.autedi.domain.invitation.dto.response.InvitationResponse;
import com.ceos24.autedi.domain.invitation.dto.response.InvitationValidateResponse;
import com.ceos24.autedi.domain.invitation.entity.InvitationStatus;
import com.ceos24.autedi.domain.invitation.entity.UserInvitation;
import com.ceos24.autedi.domain.invitation.repository.UserInvitationRepository;
import com.ceos24.autedi.domain.user.entity.Role;
import com.ceos24.autedi.domain.user.entity.User;
import com.ceos24.autedi.domain.user.entity.UserStatus;
import com.ceos24.autedi.domain.user.repository.UserRepository;
import com.ceos24.autedi.global.email.EmailService;
import com.ceos24.autedi.global.exception.CustomException;
import com.ceos24.autedi.global.exception.ErrorCode;
import com.ceos24.autedi.global.util.TokenHashUtil;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InvitationService {

    private final UserInvitationRepository userInvitationRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public InvitationResponse createInvitation(Long directorId, InvitationCreateRequest request) {
        User director = userRepository.findById(directorId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        if (director.getRole() != Role.DIRECTOR) {
            throw new CustomException(ErrorCode.ACCESS_DENIED);
        }

        Academy academy = director.getAcademy();
        if (!academy.isActive()) {
            throw new CustomException(ErrorCode.ACADEMY_SUBSCRIPTION_INACTIVE);
        }

        if (userRepository.existsByEmail(request.email())) {
            throw new CustomException(ErrorCode.ALREADY_REGISTERED_EMAIL);
        }

        // 재초대 처리: 동일 이메일에 대기 중인 기존 초대가 있다면 무효화(REVOKED)
        List<UserInvitation> existingInvitations = userInvitationRepository
                .findAllByEmailAndStatus(request.email(), InvitationStatus.PENDING);
        existingInvitations.forEach(UserInvitation::revoke);

        String rawToken = TokenHashUtil.generateRawToken();
        String tokenHash = TokenHashUtil.hashToken(rawToken);
        LocalDateTime expiresAt = LocalDateTime.now().plusHours(24);

        UserInvitation invitation = UserInvitation.builder()
                .academy(academy)
                .invitedBy(director)
                .email(request.email())
                .tokenHash(tokenHash)
                .expiresAt(expiresAt)
                .build();
        userInvitationRepository.save(invitation);

        // 이메일 비동기 발송
        emailService.sendInvitationEmail(request.email(), academy.getName(), rawToken);

        return InvitationResponse.from(invitation);
    }

    @Transactional
    public InvitationValidateResponse validateInvitation(String rawToken) {
        String tokenHash = TokenHashUtil.hashToken(rawToken);
        UserInvitation invitation = userInvitationRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_INVITATION_TOKEN));

        LocalDateTime now = LocalDateTime.now();
        if (invitation.getStatus() != InvitationStatus.PENDING) {
            throw new CustomException(ErrorCode.ALREADY_USED_INVITATION);
        }

        if (invitation.getExpiresAt().isBefore(now)) {
            invitation.expire();
            throw new CustomException(ErrorCode.EXPIRED_INVITATION_TOKEN);
        }

        return new InvitationValidateResponse(
                true,
                invitation.getEmail(),
                invitation.getAcademy().getName(),
                invitation.getExpiresAt()
        );
    }

    @Transactional
    public SignupResponse signup(SignupRequest request) {
        String tokenHash = TokenHashUtil.hashToken(request.token());

        // 동시성 제어를 위한 비관적 락 조회
        UserInvitation invitation = userInvitationRepository.findByTokenHashWithLock(tokenHash)
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_INVITATION_TOKEN));

        LocalDateTime now = LocalDateTime.now();
        if (invitation.getStatus() != InvitationStatus.PENDING) {
            throw new CustomException(ErrorCode.ALREADY_USED_INVITATION);
        }

        if (invitation.getExpiresAt().isBefore(now)) {
            invitation.expire();
            throw new CustomException(ErrorCode.EXPIRED_INVITATION_TOKEN);
        }

        if (!invitation.getEmail().equalsIgnoreCase(request.email())) {
            throw new CustomException(ErrorCode.INVITATION_EMAIL_MISMATCH);
        }

        if (userRepository.existsByEmail(request.email())) {
            throw new CustomException(ErrorCode.ALREADY_REGISTERED_EMAIL);
        }

        User teacher = User.builder()
                .academy(invitation.getAcademy())
                .email(invitation.getEmail())
                .passwordHash(passwordEncoder.encode(request.password()))
                .name(request.name())
                .role(Role.TEACHER)
                .status(UserStatus.ACTIVE)
                .invitedBy(invitation.getInvitedBy())
                .build();
        userRepository.save(teacher);

        invitation.accept(now);

        return SignupResponse.from(teacher);
    }
}
