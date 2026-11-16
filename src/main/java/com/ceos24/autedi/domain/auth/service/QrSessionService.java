package com.ceos24.autedi.domain.auth.service;

import com.ceos24.autedi.domain.auth.dto.request.QrSessionCreateRequest;
import com.ceos24.autedi.domain.auth.dto.response.QrLoginResponse;
import com.ceos24.autedi.domain.auth.dto.response.QrSessionCreateResponse;
import com.ceos24.autedi.domain.auth.dto.response.QrSessionStatusResponse;
import com.ceos24.autedi.domain.auth.entity.QrSession;
import com.ceos24.autedi.domain.auth.entity.QrSessionStatus;
import com.ceos24.autedi.domain.auth.event.QrSessionLinked;
import com.ceos24.autedi.domain.auth.event.QrSessionScanned;
import com.ceos24.autedi.domain.auth.repository.QrSessionRepository;
import com.ceos24.autedi.domain.user.entity.User;
import com.ceos24.autedi.domain.user.repository.UserRepository;
import com.ceos24.autedi.domain.worksheet.entity.Worksheet;
import com.ceos24.autedi.domain.worksheet.repository.WorksheetRepository;
import com.ceos24.autedi.global.exception.CustomException;
import com.ceos24.autedi.global.exception.ErrorCode;
import com.ceos24.autedi.global.util.QrCodeGenerator;
import com.ceos24.autedi.global.util.TokenHashUtil;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * QR 로그인 세션 서비스.
 *
 * <p>상태 전환은 조건부 UPDATE(WHERE status = ?)로만 수행하고 영향 행이 0이면 실패 처리한다. 실패 시 만료 처리(EXPIRED 전환)가 커밋되도록
 * CustomException에 대해서는 롤백하지 않는다. 따라서 상태를 바꾸는 UPDATE 성공 이후에는 CustomException을 던지지 않는다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class QrSessionService {

    private static final int QR_IMAGE_SIZE = 300;
    private static final Set<QrSessionStatus> ACTIVE_STATUSES =
            EnumSet.of(QrSessionStatus.PENDING, QrSessionStatus.SCANNED, QrSessionStatus.APPROVED);

    private final QrSessionRepository qrSessionRepository;
    private final UserRepository userRepository;
    private final WorksheetRepository worksheetRepository;
    private final AuthService authService;
    private final QrCodeGenerator qrCodeGenerator;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${app.qr.base-url}")
    private String baseUrl;

    @Value("${app.qr.expiration-minutes:3}")
    private long expirationMinutes;

    @Transactional
    public QrSessionCreateResponse create(Long userId, Long academyId, QrSessionCreateRequest request) {
        // worksheetId가 없으면 첫 사진용 QR이다(휴대폰이 올린 사진으로 새 작업을 만든다).
        // 있으면 그 작업의 사진 교체용이다: 없거나 다른 학원의 작업은 WORKSHEET_001, 같은 학원이지만 소유자가 아니면 AUTH_002.
        Long worksheetId = request == null ? null : request.worksheetId();
        if (worksheetId != null) {
            Worksheet worksheet = worksheetRepository
                    .findByIdAndAcademyId(worksheetId, academyId)
                    .orElseThrow(() -> new CustomException(ErrorCode.WORKSHEET_NOT_FOUND));
            if (!worksheet.getUser().getId().equals(userId)) {
                throw new CustomException(ErrorCode.ACCESS_DENIED);
            }
        }
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(expirationMinutes);
        QrSession session = QrSession.builder()
                .sessionId(UUID.randomUUID().toString())
                .qrToken(TokenHashUtil.generateRawToken())
                .userId(userId)
                .worksheetId(worksheetId)
                .expiresAt(expiresAt)
                .build();
        qrSessionRepository.save(session);

        String qrImage = qrCodeGenerator.toDataUri(buildQrUrl(session.getQrToken()), QR_IMAGE_SIZE);
        return new QrSessionCreateResponse(session.getSessionId(), expiresAt, qrImage);
    }

    @Transactional(noRollbackFor = CustomException.class)
    public void scan(String qrToken, String deviceInfo) {
        int updated = qrSessionRepository.transitionByQrToken(
                qrToken, QrSessionStatus.PENDING, QrSessionStatus.SCANNED, LocalDateTime.now());
        if (updated == 0) {
            throw failureByQrToken(qrToken);
        }
        QrSession session = qrSessionRepository
                .findByQrToken(qrToken)
                .orElseThrow(() -> new CustomException(ErrorCode.QR_SESSION_NOT_FOUND));
        eventPublisher.publishEvent(new QrSessionScanned(session.getUserId(), session.getSessionId(), deviceInfo));
    }

    /**
     * 이미 로그인된 폰이 승인 없이 QR 세션에 연결한다. 토큰의 사용자와 세션 사용자가 같을 때만 PENDING에서 바로 COMPLETED로 전환하고 새
     * qrSessionId가 담긴 토큰을 발급한다. 모든 사용자 검증은 상태 변경 UPDATE 전에 끝낸다.
     */
    @Transactional(noRollbackFor = CustomException.class)
    public QrLoginResponse link(Long userId, String qrToken, String deviceInfo) {
        QrSession session = qrSessionRepository
                .findByQrToken(qrToken)
                .orElseThrow(() -> new CustomException(ErrorCode.QR_SESSION_NOT_FOUND));
        LocalDateTime now = LocalDateTime.now();
        if (session.getStatus() != QrSessionStatus.PENDING || session.isExpired(now)) {
            throw failure(session, now);
        }
        if (!session.isOwnedBy(userId)) {
            throw new CustomException(ErrorCode.QR_USER_MISMATCH);
        }

        User user = userRepository.findById(userId).orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        if (!user.isActive()) {
            throw new CustomException(ErrorCode.USER_INACTIVE);
        }

        // 동시 요청 중 단 하나만 영향 행 1을 얻는다. 이후에는 CustomException을 던지지 않는다.
        int claimed = qrSessionRepository.transitionByQrTokenAndUser(
                qrToken, userId, QrSessionStatus.PENDING, QrSessionStatus.COMPLETED, now);
        if (claimed == 0) {
            throw failureByQrToken(qrToken);
        }

        User managed = userRepository.findById(userId).orElseThrow();
        managed.updateLastLoginAt(now);
        QrLoginResponse response =
                QrLoginResponse.of(authService.issueTokens(managed, session.getSessionId()), session.getWorksheetId());
        eventPublisher.publishEvent(new QrSessionLinked(userId, session.getSessionId(), deviceInfo));
        return response;
    }

    @Transactional(noRollbackFor = CustomException.class)
    public void approve(Long userId, String sessionId) {
        transitionByOwner(userId, sessionId, List.of(QrSessionStatus.SCANNED), QrSessionStatus.APPROVED);
    }

    @Transactional(noRollbackFor = CustomException.class)
    public void reject(Long userId, String sessionId) {
        transitionByOwner(
                userId, sessionId, List.of(QrSessionStatus.PENDING, QrSessionStatus.SCANNED), QrSessionStatus.REJECTED);
    }

    @Transactional(noRollbackFor = CustomException.class)
    public QrSessionStatusResponse getStatus(String qrToken) {
        QrSession session = qrSessionRepository
                .findByQrToken(qrToken)
                .orElseThrow(() -> new CustomException(ErrorCode.QR_SESSION_NOT_FOUND));
        if (qrSessionRepository.expireIfOverdue(session.getId(), ACTIVE_STATUSES, LocalDateTime.now()) > 0) {
            return new QrSessionStatusResponse(QrSessionStatus.EXPIRED);
        }
        return new QrSessionStatusResponse(session.getStatus());
    }

    @Transactional(noRollbackFor = CustomException.class)
    public QrLoginResponse issueToken(String qrToken) {
        QrSession session = qrSessionRepository
                .findByQrToken(qrToken)
                .orElseThrow(() -> new CustomException(ErrorCode.QR_SESSION_NOT_FOUND));
        LocalDateTime now = LocalDateTime.now();
        if (session.getStatus() != QrSessionStatus.APPROVED || session.isExpired(now)) {
            throw failure(session, now);
        }

        // 상태 전환 이후에는 CustomException을 던지지 않도록 사용자 검증을 먼저 한다.
        User user = userRepository
                .findById(session.getUserId())
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        if (!user.isActive()) {
            throw new CustomException(ErrorCode.USER_INACTIVE);
        }

        // 동시 요청 중 단 하나만 영향 행 1을 얻는다.
        int claimed = qrSessionRepository.transitionByQrToken(
                qrToken, QrSessionStatus.APPROVED, QrSessionStatus.COMPLETED, now);
        if (claimed == 0) {
            throw failureByQrToken(qrToken);
        }

        User managed = userRepository.findById(user.getId()).orElseThrow();
        managed.updateLastLoginAt(now);
        return QrLoginResponse.of(authService.issueTokens(managed, session.getSessionId()), session.getWorksheetId());
    }

    @Transactional
    public int deleteStaleSessions(LocalDateTime threshold) {
        return qrSessionRepository.deleteByExpiresAtBefore(threshold);
    }

    private void transitionByOwner(Long userId, String sessionId, List<QrSessionStatus> from, QrSessionStatus to) {
        QrSession session = qrSessionRepository
                .findBySessionId(sessionId)
                .orElseThrow(() -> new CustomException(ErrorCode.QR_SESSION_NOT_FOUND));
        if (!session.isOwnedBy(userId)) {
            throw new CustomException(ErrorCode.ACCESS_DENIED);
        }
        LocalDateTime now = LocalDateTime.now();
        int updated = qrSessionRepository.transitionBySessionId(sessionId, userId, from, to, now);
        if (updated == 0) {
            throw failure(session, now);
        }
    }

    private CustomException failureByQrToken(String qrToken) {
        return qrSessionRepository
                .findByQrToken(qrToken)
                .map(s -> failure(s, LocalDateTime.now()))
                .orElseGet(() -> new CustomException(ErrorCode.QR_SESSION_NOT_FOUND));
    }

    /** 실패 사유를 만료/상태 오류로 구분하고, 만료 시각이 지난 활성 세션은 EXPIRED로 전환한다. */
    private CustomException failure(QrSession session, LocalDateTime now) {
        if (session.getStatus() == QrSessionStatus.EXPIRED) {
            return new CustomException(ErrorCode.QR_SESSION_EXPIRED);
        }
        if (qrSessionRepository.expireIfOverdue(session.getId(), ACTIVE_STATUSES, now) > 0) {
            return new CustomException(ErrorCode.QR_SESSION_EXPIRED);
        }
        return new CustomException(ErrorCode.QR_SESSION_INVALID_STATE);
    }

    private String buildQrUrl(String qrToken) {
        String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return base + "/qr-login?token=" + qrToken;
    }
}
