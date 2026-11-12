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

    /**
     * PENDING→SCANNED→APPROVED→COMPLETED, PENDING/SCANNED→REJECTED, 종료 전 상태→EXPIRED 만 허용한다. 이미 로그인된 같은 사용자의 폰이
     * 승인 없이 연결하는 link 경로를 위해 PENDING→COMPLETED도 허용한다.
     */
    public boolean canTransitionTo(QrSessionStatus next) {
        return switch (this) {
            case PENDING -> next == SCANNED || next == COMPLETED || next == REJECTED || next == EXPIRED;
            case SCANNED -> next == APPROVED || next == REJECTED || next == EXPIRED;
            case APPROVED -> next == COMPLETED || next == EXPIRED;
            case COMPLETED, REJECTED, EXPIRED -> false;
        };
    }
}
