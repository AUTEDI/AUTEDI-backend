package com.ceos24.autedi.domain.auth.event;

import com.ceos24.autedi.global.sse.SseEventPublisher;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * QR 도메인 이벤트를 SSE로 전달한다. 커밋된 변경만 전송하고(AFTER_COMMIT), 전송은 전용 executor에서 수행한다.
 * 수신자는 QR 세션을 만든 사용자이며 payload에는 qrToken을 담지 않는다.
 */
@Component
@RequiredArgsConstructor
public class QrSessionSseListener {

    public static final String EVENT_QR_SCANNED = "qr.scanned";

    private final SseEventPublisher publisher;

    @Async("sseExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onScanned(QrSessionScanned event) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sessionId", event.sessionId());
        payload.put("deviceInfo", event.deviceInfo());
        publisher.publish(event.userId(), EVENT_QR_SCANNED, payload);
    }
}
