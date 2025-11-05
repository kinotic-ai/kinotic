package org.kinotic.structures.internal.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Configuration for cache eviction event handling
 * 
 * NOTE: Currently all event listeners are synchronous for simplicity.
 * This configuration is available for future use if async processing is needed.
 */
@Configuration
@EnableAsync
public class CacheEvictionConfiguration {

    /**
     * Task executor for async cache eviction events
     * This allows cache eviction events to be processed asynchronously
     * without blocking the main thread
     * 
     * To use: Add @Async("cacheEvictionTaskExecutor") to event listener methods
     */
    @Bean("cacheEvictionTaskExecutor")
    public TaskExecutor cacheEvictionTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("cache-eviction-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
