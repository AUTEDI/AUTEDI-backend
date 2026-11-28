package com.ceos24.autedi.domain.generation.service;

import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** 단일 JVM의 첨삭·인사이트 전체 실행 예산. 인사이트 대기열은 만들지 않는다. */
@Component
public class AiExecutionLimiter {
    private final Semaphore total;
    private final Semaphore insight = new Semaphore(1);

    public AiExecutionLimiter(@Value("${app.generation.worker-count:2}") int workers) {
        if (workers < 1) throw new IllegalArgumentException("AI 실행 한도는 1 이상이어야 합니다.");
        total = new Semaphore(workers);
    }

    public Permit tryCorrection() {
        return total.tryAcquire() ? new Permit(total, null) : null;
    }

    public Permit tryInsight() {
        if (!insight.tryAcquire()) return null;
        if (total.tryAcquire()) return new Permit(total, insight);
        insight.release();
        return null;
    }

    public static final class Permit implements AutoCloseable {
        private final Semaphore total;
        private final Semaphore insight;
        private final AtomicBoolean closed = new AtomicBoolean();

        private Permit(Semaphore total, Semaphore insight) {
            this.total = total;
            this.insight = insight;
        }

        @Override
        public void close() {
            if (!closed.compareAndSet(false, true)) return;
            total.release();
            if (insight != null) insight.release();
        }
    }
}
