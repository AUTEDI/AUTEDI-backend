package com.ceos24.autedi.domain.auth.entity;

import java.util.EnumSet;
import java.util.Set;

public enum QrSessionStatus {
    PENDING,
    SCANNED,
    APPROVED,
    COMPLETED,
    REJECTED,
    EXPIRED;

    private static final Set<QrSessionStatus> TERMINAL = EnumSet.of(COMPLETED, REJECTED, EXPIRED);

    public boolean isTerminal() {
        return TERMINAL.contains(this);
    }

    /** PENDING→SCANNED→APPROVED→COMPLETED, PENDING/SCANNED→REJECTED, 종료 전 상태→EXPIRED 만 허용한다. */
    public boolean canTransitionTo(QrSessionStatus next) {
        return switch (this) {
            case PENDING -> next == SCANNED || next == REJECTED || next == EXPIRED;
            case SCANNED -> next == APPROVED || next == REJECTED || next == EXPIRED;
            case APPROVED -> next == COMPLETED || next == EXPIRED;
            case COMPLETED, REJECTED, EXPIRED -> false;
        };
    }
}
