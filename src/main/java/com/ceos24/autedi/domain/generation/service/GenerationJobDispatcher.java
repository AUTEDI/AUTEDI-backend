package com.ceos24.autedi.domain.generation.service;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
public class GenerationJobDispatcher {
    private final TaskExecutor executor;
    private final GenerationWorker worker;
    private final GenerationWorkStore store;
    private final Set<Long> submitted = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean ready = new AtomicBoolean();

    public GenerationJobDispatcher(
            @Qualifier("generationExecutor") TaskExecutor executor,
            GenerationWorker worker,
            GenerationWorkStore store) {
        this.executor = executor;
        this.worker = worker;
        this.store = store;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void afterCommit(GenerationRequested event) {
        submit(event.generationId());
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recoverAfterRestart() {
        // 단일 서버 워커 기준. 실행 중이던 외부 처리는 자동 재호출하지 않는다.
        for (Long id : store.interruptedIds())
            store.fail(id, "SERVER_INTERRUPTED", "서버 중단으로 실행 여부를 확인할 수 없습니다. 수동 재시도가 필요합니다.", true);
        ready.set(true);
        dispatchPending();
    }

    @Scheduled(fixedDelayString = "${app.generation.dispatch-interval-ms:2000}")
    public void dispatchPending() {
        if (!ready.get()) return;
        for (Long id : store.pendingIds()) submit(id);
    }

    private void submit(Long id) {
        if (!ready.get() || !submitted.add(id)) return;
        try {
            executor.execute(() -> {
                try {
                    worker.process(id);
                } finally {
                    submitted.remove(id);
                }
            });
        } catch (TaskRejectedException e) {
            submitted.remove(id);
            log.debug("워커 대기열이 가득 차 DB 대기 작업을 다음 배정에서 처리합니다: generationId={}", id);
        }
    }
}
