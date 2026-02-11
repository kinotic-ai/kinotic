package org.kinotic.structures.support.elastic;

import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * TestContextInitializer that ensures TestContainers are ready before Spring context initialization.
 * This works in conjunction with TestBeanPostProcessor to provide a complete test setup.
 * 
 * The TestBeanPostProcessor handles the detailed container setup and property injection,
 * while this initializer ensures containers are started early in the context lifecycle.
 */
public class ElasticsearchTestContextInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
    
    private static final Logger log = LoggerFactory.getLogger(ElasticsearchTestContextInitializer.class);
    
    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        log.info("ElasticsearchTestContextInitializer: Ensuring TestContainers are ready before Spring context initialization...");
        
        try {
            // Ensure containers are started and ready before Spring context creation
            // This is a lightweight check - TestBeanPostProcessor will handle the detailed setup
            if (!ElasticsearchTestConfiguration.areContainersRunning()) {
                log.info("ElasticsearchTestContextInitializer: Starting TestContainers...");
                ElasticsearchTestConfiguration.startContainersSynchronously();
            } else if (!ElasticsearchTestConfiguration.areContainersReady()) {
                log.info("ElasticsearchTestContextInitializer: Waiting for containers to be ready...");
                ElasticsearchTestConfiguration.waitForContainersReady();
            }
            
            log.info("ElasticsearchTestContextInitializer: TestContainers are ready, proceeding with Spring context initialization");

            ElasticsearchTestConfiguration.ensureContainersReady();
  
            // Elasticsearch properties
            TestPropertyValues.of("structures.elastic-connections[0].host=" + ElasticsearchTestConfiguration.ELASTICSEARCH_CONTAINER.getHost())
                    .applyTo(applicationContext);
            TestPropertyValues.of("structures.elastic-connections[0].port=" + ElasticsearchTestConfiguration.ELASTICSEARCH_CONTAINER.getMappedPort(9200))
                    .applyTo(applicationContext);
            TestPropertyValues.of("structures.elastic-connections[0].scheme=http")
                    .applyTo(applicationContext);
            TestPropertyValues.of("spring.elasticsearch.uris=http://"+ElasticsearchTestConfiguration.ELASTICSEARCH_CONTAINER.getHost()+":"+ElasticsearchTestConfiguration.ELASTICSEARCH_CONTAINER.getMappedPort(9200))
                    .applyTo(applicationContext);

        } catch (Exception e) {
            log.error("ElasticsearchTestContextInitializer: Failed to ensure TestContainers are ready", e);
            throw new RuntimeException("TestContainers failed to start during context initialization", e);
        }
    }
}
