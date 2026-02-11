package org.kinotic.persistence.internal.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Configuration for cache eviction event handling
 * 
 * NOTE: Currently all event listeners are synchronous for simplicity.
 * This configuration is available for future use if async processing is needed.
 */
@Configuration
@EnableAsync
public class CacheEvictionConfiguration {

    @Autowired
    private ApplicationContext applicationContext;

    /**
     * Provides ApplicationEventPublisher as a Spring bean for Ignite task injection.
     * 
     * IMPORTANT: We wrap the ApplicationContext in a simple delegate to prevent
     * it from being registered as a Lifecycle bean, which can cause infinite recursion
     * during shutdown when DefaultLifecycleProcessor tries to stop lifecycle beans.
     * 
     * This allows Ignite's @SpringResource annotation to inject ApplicationEventPublisher
     * into ClusterCacheEvictionTask on remote nodes.
     */
    @Bean("applicationEventPublisher")
    public ApplicationEventPublisher applicationEventPublisher() {
        // Wrap ApplicationContext to prevent it from being treated as a Lifecycle bean
        // ApplicationContext implements both ApplicationEventPublisher and Lifecycle,
        // and returning it directly can cause infinite recursion during shutdown
        return new ApplicationEventPublisherDelegate(applicationContext);
    }
    
    /**
     * Simple delegate wrapper for ApplicationEventPublisher that prevents
     * the ApplicationContext from being registered as a Lifecycle bean.
     * This breaks the infinite recursion cycle during shutdown.
     */
    private static class ApplicationEventPublisherDelegate implements ApplicationEventPublisher {
        private final ApplicationEventPublisher delegate;
        
        public ApplicationEventPublisherDelegate(ApplicationContext applicationContext) {
            this.delegate = applicationContext;
        }
        
        @Override
        public void publishEvent(Object event) {
            delegate.publishEvent(event);
        }
    }

}
