package com.ceos24.autedi.domain.invitation.entity;

import com.ceos24.autedi.domain.academy.entity.Academy;
import com.ceos24.autedi.domain.user.entity.User;
import com.ceos24.autedi.global.entity.BaseCreatedTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "user_invitations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserInvitation extends BaseCreatedTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "academy_id", nullable = false)
    private Academy academy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invited_by", nullable = false)
    private User invitedBy;

    @Column(name = "email", length = 255, nullable = false)
    private String email;

    @Column(name = "token_hash", length = 64, nullable = false, unique = true)
    private String tokenHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private InvitationStatus status;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "accepted_at")
    private LocalDateTime acceptedAt;

    @Builder
    public UserInvitation(Academy academy, User invitedBy, String email, String tokenHash, LocalDateTime expiresAt) {
        this.academy = academy;
        this.invitedBy = invitedBy;
        this.email = email;
        this.tokenHash = tokenHash;
        this.status = InvitationStatus.PENDING;
        this.expiresAt = expiresAt;
    }

    public void revoke() {
        this.status = InvitationStatus.REVOKED;
    }

    public void expire() {
        this.status = InvitationStatus.EXPIRED;
    }

    public void accept(LocalDateTime acceptedAt) {
        this.status = InvitationStatus.ACCEPTED;
        this.acceptedAt = acceptedAt;
    }

    public boolean isPendingAndNotExpired(LocalDateTime now) {
        return this.status == InvitationStatus.PENDING && this.expiresAt.isAfter(now);
    }
}
