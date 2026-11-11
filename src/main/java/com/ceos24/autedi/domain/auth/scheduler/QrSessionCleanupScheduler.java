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
