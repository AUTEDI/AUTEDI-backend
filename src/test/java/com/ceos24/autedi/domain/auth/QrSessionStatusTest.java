package com.ceos24.autedi.domain.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ceos24.autedi.domain.auth.entity.QrSession;
import com.ceos24.autedi.domain.auth.entity.QrSessionStatus;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class QrSessionStatusTest {

    private QrSession newSession() {
        return QrSession.builder()
                .sessionId("s")
                .qrToken("t")
                .userId(1L)
                .expiresAt(LocalDateTime.now().plusMinutes(3))
                .build();
    }

    @Test
    @DisplayName("정상 경로 PENDING→SCANNED→APPROVED→COMPLETED 전이 허용")
    void happyPath() {
        QrSession s = newSession();
        assertThat(s.getStatus()).isEqualTo(QrSessionStatus.PENDING);
        s.scan();
        s.approve();
        s.complete();
        assertThat(s.getStatus()).isEqualTo(QrSessionStatus.COMPLETED);
    }

    @Test
    @DisplayName("PENDING/SCANNED→REJECTED, EXPIRED 허용")
    void rejectAndExpire() {
        QrSession a = newSession();
        a.reject();
        assertThat(a.getStatus()).isEqualTo(QrSessionStatus.REJECTED);

        QrSession b = newSession();
        b.scan();
        b.reject();
        assertThat(b.getStatus()).isEqualTo(QrSessionStatus.REJECTED);

        QrSession c = newSession();
        c.expire();
        assertThat(c.getStatus()).isEqualTo(QrSessionStatus.EXPIRED);
    }

    @Test
    @DisplayName("건너뛰기 전이 거부: PENDING→APPROVED, PENDING→COMPLETED, SCANNED→COMPLETED")
    void skipRejected() {
        assertThatThrownBy(() -> newSession().approve()).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> newSession().complete()).isInstanceOf(IllegalStateException.class);
        QrSession s = newSession();
        s.scan();
        assertThatThrownBy(s::complete).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("역방향 및 중복 전이 거부")
    void backwardAndDuplicateRejected() {
        QrSession s = newSession();
        s.scan();
        assertThatThrownBy(s::scan).isInstanceOf(IllegalStateException.class);
        s.approve();
        assertThatThrownBy(s::scan).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(s::reject).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("종료 상태(COMPLETED/REJECTED/EXPIRED)에서는 어떤 전이도 거부")
    void terminalStatesFrozen() {
        for (QrSessionStatus terminal :
                new QrSessionStatus[] {QrSessionStatus.COMPLETED, QrSessionStatus.REJECTED, QrSessionStatus.EXPIRED}) {
            for (QrSessionStatus next : QrSessionStatus.values()) {
                assertThat(terminal.canTransitionTo(next)).isFalse();
            }
        }
    }

    @Test
    @DisplayName("만료 판정은 expiresAt 이하 시각에서 true")
    void expiry() {
        QrSession s = newSession();
        assertThat(s.isExpired(LocalDateTime.now())).isFalse();
        assertThat(s.isExpired(s.getExpiresAt())).isTrue();
        assertThat(s.isExpired(s.getExpiresAt().plusSeconds(1))).isTrue();
    }
}
