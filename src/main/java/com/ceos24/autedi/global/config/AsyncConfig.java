package com.ceos24.autedi.global.config;

import com.ceos24.autedi.global.sse.SseProperties;
import java.util.concurrent.Executor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Slf4j
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "mailExecutor")
    public Executor mailExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("mail-exec-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }

    /**
     * SSE 전송 전용 executor. 첨삭 워커(generationExecutor)와 스레드를 공유하지 않는다.
     * 큐가 가득 차면 호출자를 막지 않고 해당 작업을 버린다(알림 전용, 클라이언트가 REST로 복구).
     */
    @Bean(name = "sseExecutor")
    public ThreadPoolTaskExecutor sseExecutor(SseProperties properties) {
        SseProperties.Executor conf = properties.executor();
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(conf.coreSize());
        executor.setMaxPoolSize(conf.maxSize());
        executor.setQueueCapacity(conf.queueCapacity());
        executor.setThreadNamePrefix("sse-exec-");
        executor.setWaitForTasksToCompleteOnShutdown(false);
        executor.setRejectedExecutionHandler((task, pool) -> log.warn(
                "SSE executor 포화로 전송 작업을 버립니다. queue={}", pool.getQueue().size()));
        return executor;
    }
}
