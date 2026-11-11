package com.ceos24.autedi.domain.auth.entity;

import com.ceos24.autedi.global.entity.BaseCreatedTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "qr_session")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class QrSession extends BaseCreatedTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 노트북이 사용하는 식별자 (UUID v4). QR에는 담지 않는다. */
    @Column(name = "session_id", length = 36, nullable = false, unique = true, updatable = false)
    private String sessionId;

    /** 휴대폰(QR)이 사용하는 일회용 토큰. */
    @Column(name = "qr_token", length = 64, nullable = false, unique = true, updatable = false)
    private String qrToken;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "worksheet_id")
    private Long worksheetId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private QrSessionStatus status;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Builder
    public QrSession(String sessionId, String qrToken, Long userId, Long worksheetId, LocalDateTime expiresAt) {
        this.sessionId = sessionId;
        this.qrToken = qrToken;
        this.userId = userId;
        this.worksheetId = worksheetId;
        this.expiresAt = expiresAt;
        this.status = QrSessionStatus.PENDING;
    }

    public boolean isExpired(LocalDateTime now) {
        return !expiresAt.isAfter(now);
    }

    public boolean isOwnedBy(Long userId) {
        return this.userId.equals(userId);
    }

    public void scan() {
        transitionTo(QrSessionStatus.SCANNED);
    }

    public void approve() {
        transitionTo(QrSessionStatus.APPROVED);
    }

    public void complete() {
        transitionTo(QrSessionStatus.COMPLETED);
    }

    public void reject() {
        transitionTo(QrSessionStatus.REJECTED);
    }

    public void expire() {
        transitionTo(QrSessionStatus.EXPIRED);
    }

    private void transitionTo(QrSessionStatus next) {
        if (!status.canTransitionTo(next)) {
            throw new IllegalStateException("허용되지 않는 QR 세션 상태 전이: " + status + " -> " + next);
        }
        this.status = next;
    }
}
