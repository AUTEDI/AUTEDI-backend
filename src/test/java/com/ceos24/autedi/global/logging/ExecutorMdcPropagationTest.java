package com.ceos24.autedi.global.logging;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;

/** 실제 스프링 빈으로 만들어진 3개 executor에 MdcTaskDecorator가 연결되어 있는지 확인한다. */
@SpringBootTest
class ExecutorMdcPropagationTest {

    @Autowired
    @Qualifier("mailExecutor")
    private Executor mailExecutor;

    @Autowired
    @Qualifier("sseExecutor")
    private Executor sseExecutor;

    @Autowired
    @Qualifier("generationExecutor")
    private Executor generationExecutor;

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    @DisplayName("mailExecutor는 맡긴 스레드의 MDC를 작업 스레드로 전달한다")
    void mailExecutorPropagatesMdc() throws Exception {
        assertPropagates(mailExecutor);
    }

    @Test
    @DisplayName("sseExecutor는 맡긴 스레드의 MDC를 작업 스레드로 전달한다")
    void sseExecutorPropagatesMdc() throws Exception {
        assertPropagates(sseExecutor);
    }

    @Test
    @DisplayName("generationExecutor는 맡긴 스레드의 MDC를 작업 스레드로 전달한다")
    void generationExecutorPropagatesMdc() throws Exception {
        assertPropagates(generationExecutor);
    }

    private void assertPropagates(Executor executor) throws Exception {
        MDC.put(MdcKeys.REQUEST_ID, "req-wired");
        MDC.put(MdcKeys.USER_ID, "7");
        CompletableFuture<Map<String, String>> seen = new CompletableFuture<>();

        executor.execute(() -> seen.complete(MDC.getCopyOfContextMap()));

        assertThat(seen.get(5, TimeUnit.SECONDS))
                .containsEntry(MdcKeys.REQUEST_ID, "req-wired")
                .containsEntry(MdcKeys.USER_ID, "7");
    }
}
