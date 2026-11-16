package com.ceos24.autedi.domain.generation.event;

import com.ceos24.autedi.global.sse.SseEventPublisher;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 첨삭 진행 이벤트를 학습지 소유자에게 SSE로 전달한다. 커밋된 변경만 전송하고(AFTER_COMMIT) 전송은 워커가 아닌 전용 executor에서 수행한다.
 * payload에는 ID와 최소 정보만 담는다(결과 본문, 오류 메시지, 토큰 제외).
 */
@Component
@RequiredArgsConstructor
public class GenerationSseListener {

    public static final String EVENT_STARTED = "generation.started";
    public static final String EVENT_PROGRESS = "generation.progress";
    public static final String EVENT_SUCCEEDED = "generation.succeeded";
    public static final String EVENT_FAILED = "generation.failed";

    private final SseEventPublisher publisher;

    @Async("sseExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onStarted(GenerationStarted event) {
        publisher.publish(event.userId(), EVENT_STARTED, ids(event.worksheetId(), event.generationId()));
    }

    @Async("sseExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onProgressed(GenerationProgressed event) {
        Map<String, Object> payload = ids(event.worksheetId(), event.generationId());
        payload.put("stage", event.stage().name());
        publisher.publish(event.userId(), EVENT_PROGRESS, payload);
    }

    @Async("sseExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onFinished(GenerationFinished event) {
        Map<String, Object> payload = ids(event.worksheetId(), event.generationId());
        if (event.succeeded()) {
            publisher.publish(event.userId(), EVENT_SUCCEEDED, payload);
            return;
        }
        payload.put("errorCode", event.errorCode());
        payload.put("retryable", event.retryable());
        publisher.publish(event.userId(), EVENT_FAILED, payload);
    }

    private static Map<String, Object> ids(Long worksheetId, Long generationId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("worksheetId", worksheetId);
        payload.put("generationId", generationId);
        return payload;
    }
}
