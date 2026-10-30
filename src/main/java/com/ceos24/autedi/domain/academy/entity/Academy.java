package com.ceos24.autedi.domain.academy.entity;

import com.ceos24.autedi.global.entity.BaseTimeEntity;
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
@Table(name = "academies")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Academy extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", length = 100, nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "subscription_status", length = 20, nullable = false)
    private SubscriptionStatus subscriptionStatus;

    @Column(name = "plan_expires_at")
    private LocalDateTime planExpiresAt;

    @Builder
    public Academy(String name, SubscriptionStatus subscriptionStatus, LocalDateTime planExpiresAt) {
        this.name = name;
        this.subscriptionStatus = subscriptionStatus != null ? subscriptionStatus : SubscriptionStatus.ACTIVE;
        this.planExpiresAt = planExpiresAt;
    }

    public boolean isActive() {
        return this.subscriptionStatus == SubscriptionStatus.ACTIVE;
    }
}
