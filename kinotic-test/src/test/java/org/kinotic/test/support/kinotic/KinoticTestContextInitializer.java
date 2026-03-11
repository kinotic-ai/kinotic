package org.kinotic.test.support.kinotic;

import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * TestContextInitializer that starts the Kinotic Docker Compose stack
 * compose.kinotic-test.yml before Spring context initialization
 * and injects connection properties for Elasticsearch (syncs compose ports with real Spring properties).
 */
public class KinoticTestContextInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    private static final Logger log = LoggerFactory.getLogger(KinoticTestContextInitializer.class);

    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        log.info("KinoticTestContextInitializer: Ensuring Kinotic Compose stack is ready...");

        try {
            if (!KinoticTestConfiguration.areContainersRunning()) {
                log.info("KinoticTestContextInitializer: Starting Docker Compose...");
                KinoticTestConfiguration.startContainersSynchronously();
            } else if (!KinoticTestConfiguration.areContainersReady()) {
                log.info("KinoticTestContextInitializer: Waiting for containers...");
                KinoticTestConfiguration.waitForContainersReady();
            }

            KinoticTestConfiguration.ensureContainersReady();

            String esHost = KinoticTestConfiguration.getElasticsearchHost();
            int esPort = KinoticTestConfiguration.getElasticsearchPort();

            TestPropertyValues.of("kinotic.persistence.elastic-connections[0].host=" + esHost)
                .applyTo(applicationContext);
            TestPropertyValues.of("kinotic.persistence.elastic-connections[0].port=" + esPort)
                .applyTo(applicationContext);
            TestPropertyValues.of("kinotic.persistence.elastic-connections[0].scheme=http")
                .applyTo(applicationContext);

            log.info("KinoticTestContextInitializer: Kinotic stack ready, elasticsearch={}:{}", esHost, esPort);

        } catch (Exception e) {
            log.error("KinoticTestContextInitializer: Failed to ensure Kinotic Compose is ready", e);
            throw new RuntimeException("Kinotic Compose failed to start during context initialization", e);
        }
    }
}
