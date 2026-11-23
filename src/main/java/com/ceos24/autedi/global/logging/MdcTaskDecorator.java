package com.ceos24.autedi.global.logging;

import java.util.Map;
import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;

/**
 * 작업을 맡기는 순간의 MDC를 복사해 두었다가, 작업을 실행하는 스레드가 시작할 때 복원하고 끝나면 시작 전 상태로 되돌린다.
 * 워커 스레드는 재사용되므로 값이 다음 작업으로 새면 안 된다.
 */
public class MdcTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable task) {
        Map<String, String> captured = MDC.getCopyOfContextMap();
        return () -> {
            Map<String, String> previous = MDC.getCopyOfContextMap();
            apply(captured);
            try {
                task.run();
            } finally {
                apply(previous);
            }
        };
    }

    private static void apply(Map<String, String> context) {
        if (context == null || context.isEmpty()) {
            MDC.clear();
        } else {
            MDC.setContextMap(context);
        }
    }
}
