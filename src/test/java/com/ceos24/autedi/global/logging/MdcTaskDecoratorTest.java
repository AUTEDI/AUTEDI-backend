package com.ceos24.autedi.global.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

/**
 * 워커 스레드를 하나만 쓰는 풀로 "같은 스레드가 재사용되는 상황"을 만든다. 데코레이터 없이 실행하는 작업({@link #readMdcOnWorker})으로 앞선 작업이
 * 스레드에 값을 남겼는지 확인한다.
 */
class MdcTaskDecoratorTest {

    private final MdcTaskDecorator decorator = new MdcTaskDecorator();
    private ExecutorService worker;

    @BeforeEach
    void setUp() {
        worker = Executors.newSingleThreadExecutor();
    }

    @AfterEach
    void tearDown() {
        worker.shutdownNow();
        MDC.clear();
    }

    @Test
    @DisplayName("작업을 맡긴 스레드의 MDC가 작업을 실행하는 스레드에서 보인다")
    void propagatesMdcToWorkerThread() throws Exception {
        MDC.put(MdcKeys.REQUEST_ID, "req-1");
        MDC.put(MdcKeys.USER_ID, "12");
        AtomicReference<Map<String, String>> seen = new AtomicReference<>();

        worker.submit(decorator.decorate(() -> seen.set(MDC.getCopyOfContextMap())))
                .get();

        assertThat(seen.get()).containsEntry(MdcKeys.REQUEST_ID, "req-1").containsEntry(MdcKeys.USER_ID, "12");
    }

    @Test
    @DisplayName("맡긴 쪽이 먼저 MDC를 비워도 맡길 때 복사해 둔 값이 유지된다")
    void keepsCopyEvenIfCallerClearsMdcFirst() throws Exception {
        MDC.put(MdcKeys.REQUEST_ID, "req-1");
        AtomicReference<String> seen = new AtomicReference<>();
        Runnable decorated = decorator.decorate(() -> seen.set(MDC.get(MdcKeys.REQUEST_ID)));

        MDC.clear();
        worker.submit(decorated).get();

        assertThat(seen.get()).isEqualTo("req-1");
    }

    @Test
    @DisplayName("작업이 끝나면 워커 스레드의 MDC는 비워져서 다음 작업으로 새지 않는다")
    void clearsWorkerMdcAfterTask() throws Exception {
        MDC.put(MdcKeys.REQUEST_ID, "req-1");

        worker.submit(decorator.decorate(() -> {})).get();

        assertThat(readMdcOnWorker()).isNullOrEmpty();
    }

    @Test
    @DisplayName("작업 중 예외가 나도 워커 스레드의 MDC는 비워진다")
    void clearsWorkerMdcWhenTaskThrows() throws Exception {
        MDC.put(MdcKeys.REQUEST_ID, "req-1");

        assertThatThrownBy(() -> worker.submit(decorator.decorate(() -> {
                            throw new IllegalStateException("boom");
                        }))
                        .get())
                .hasCauseInstanceOf(IllegalStateException.class);

        assertThat(readMdcOnWorker()).isNullOrEmpty();
    }

    @Test
    @DisplayName("맡긴 쪽 MDC가 비어 있으면 작업 안에서도 비어 있다 (요청 없이 시작된 작업)")
    void taskStartedWithoutRequestHasEmptyMdc() throws Exception {
        AtomicReference<Map<String, String>> seen = new AtomicReference<>();
        // 워커 스레드에 이전 작업의 값이 남아 있더라도 새 작업으로 새어 들어가면 안 된다.
        worker.submit(() -> MDC.put(MdcKeys.REQUEST_ID, "stale")).get();

        worker.submit(decorator.decorate(() -> seen.set(MDC.getCopyOfContextMap())))
                .get();

        assertThat(seen.get()).isNullOrEmpty();
    }

    @Test
    @DisplayName("맡긴 스레드가 작업을 직접 실행해도 그 스레드의 원래 MDC가 유지된다")
    void restoresCallerMdcWhenCallerRunsTask() {
        MDC.put(MdcKeys.REQUEST_ID, "origin");
        AtomicReference<String> seenInTask = new AtomicReference<>();
        Runnable decorated = decorator.decorate(() -> seenInTask.set(MDC.get(MdcKeys.REQUEST_ID)));

        // 같은 스레드가 다른 요청(caller)을 처리 중일 때 작업이 이 스레드에서 직접 실행되는 상황
        MDC.put(MdcKeys.REQUEST_ID, "caller");
        decorated.run();

        assertThat(seenInTask.get()).isEqualTo("origin");
        assertThat(MDC.get(MdcKeys.REQUEST_ID)).isEqualTo("caller");
    }

    @Test
    @DisplayName("직접 실행한 작업이 예외를 던져도 그 스레드의 원래 MDC가 유지된다")
    void restoresCallerMdcWhenCallerRunsFailingTask() {
        MDC.put(MdcKeys.REQUEST_ID, "origin");
        Runnable decorated = decorator.decorate(() -> {
            throw new IllegalStateException("boom");
        });

        MDC.put(MdcKeys.REQUEST_ID, "caller");

        assertThatThrownBy(decorated::run).isInstanceOf(IllegalStateException.class);
        assertThat(MDC.get(MdcKeys.REQUEST_ID)).isEqualTo("caller");
    }

    private Map<String, String> readMdcOnWorker() throws Exception {
        return worker.submit(MDC::getCopyOfContextMap).get();
    }
}
