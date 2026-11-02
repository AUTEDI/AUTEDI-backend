package com.ceos24.autedi.global.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableScheduling
public class GenerationExecutionConfig {
    @Bean
    public ThreadPoolTaskExecutor generationExecutor(
            @Value("${app.generation.worker-count:2}") int workers,
            @Value("${app.generation.queue-capacity:16}") int capacity) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(workers);
        executor.setMaxPoolSize(workers);
        executor.setQueueCapacity(capacity);
        executor.setThreadNamePrefix("generation-");
        executor.setWaitForTasksToCompleteOnShutdown(false);
        executor.setAwaitTerminationSeconds(5);
        return executor;
    }
}
