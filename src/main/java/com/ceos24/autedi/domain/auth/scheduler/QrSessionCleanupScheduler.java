package com.ceos24.autedi.domain.auth.scheduler;

import com.ceos24.autedi.domain.auth.service.QrSessionService;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class QrSessionCleanupScheduler {

    /**
     * 만료 후 보관 시간. QR 로그인으로 발급된 액세스 토큰의 수명(jwt.access-token-expiration-ms, 기본 1시간)과 맞물려 있다. 세션 행이
     * 삭제된 뒤에도 그 토큰이 살아 있으면 업로드가 QR_002로 거절되므로, 보관 시간은 액세스 토큰 수명 이상으로 유지한다. 액세스 토큰 수명을
     * 늘리면 이 값도 함께 늘려야 한다.
     */
    private static final long RETENTION_HOURS = 1;

    private final QrSessionService qrSessionService;

    @Scheduled(fixedDelayString = "${app.qr.cleanup-interval-ms:600000}")
    public void cleanup() {
        int deleted = qrSessionService.deleteStaleSessions(LocalDateTime.now().minusHours(RETENTION_HOURS));
        if (deleted > 0) {
            log.info("만료된 QR 세션 {}건 삭제", deleted);
        }
    }
}
