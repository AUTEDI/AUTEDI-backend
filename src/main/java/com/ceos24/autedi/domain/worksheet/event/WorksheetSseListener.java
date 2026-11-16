package com.ceos24.autedi.domain.worksheet.event;

import com.ceos24.autedi.global.sse.SseEventPublisher;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** 학습지 업로드 이벤트를 작업 소유자에게 SSE로 전달한다. 커밋된 변경만 전송하고 전송은 전용 executor에서 수행한다. */
@Component
@RequiredArgsConstructor
public class WorksheetSseListener {

    public static final String EVENT_WORKSHEET_CREATED = "worksheet.created";
    public static final String EVENT_WORKSHEET_FILE_REPLACED = "worksheet.file_replaced";

    private final SseEventPublisher publisher;

    @Async("sseExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCreated(WorksheetCreated event) {
        publisher.publish(event.userId(), EVENT_WORKSHEET_CREATED, payload(event.sessionId(), event.worksheetId()));
    }

    @Async("sseExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onFileReplaced(WorksheetFileReplaced event) {
        publisher.publish(
                event.userId(), EVENT_WORKSHEET_FILE_REPLACED, payload(event.sessionId(), event.worksheetId()));
    }

    private static Map<String, Object> payload(String sessionId, Long worksheetId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sessionId", sessionId);
        payload.put("worksheetId", worksheetId);
        return payload;
    }
}
