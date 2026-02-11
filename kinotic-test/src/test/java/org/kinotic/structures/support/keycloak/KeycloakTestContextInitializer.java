package org.kinotic.structures.support.keycloak;

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
public class KeycloakTestContextInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
    
    private static final Logger log = LoggerFactory.getLogger(KeycloakTestContextInitializer.class);
    
    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        log.info("TestContextInitializer: Ensuring TestContainers are ready before Spring context initialization...");
        
        try {
            // Ensure containers are started and ready before Spring context creation
            // This is a lightweight check - TestBeanPostProcessor will handle the detailed setup
            if (!KeyloakTestConfiguration.areContainersRunning()) {
                log.info("TestContextInitializer: Starting TestContainers...");
                KeyloakTestConfiguration.startContainersSynchronously();
            } else if (!KeyloakTestConfiguration.areContainersReady()) {
                log.info("TestContextInitializer: Waiting for containers to be ready...");
                KeyloakTestConfiguration.waitForContainersReady();
            }

            KeyloakTestConfiguration.ensureContainersReady();
            
            log.info("TestContextInitializer: TestContainers are ready, proceeding with Spring context initialization");

            // Keycloak OIDC properties
            String keycloakAuthUrl = KeyloakTestConfiguration.getKeycloakAuthUrl();

            // Set the dynamic Keycloak URL as an environment variable that can be referenced in YAML
            // This preserves the profile-specific configurations while allowing dynamic port usage
            System.setProperty("keycloak.test.url", keycloakAuthUrl);
            
            
        } catch (Exception e) {
            log.error("TestContextInitializer: Failed to ensure TestContainers are ready", e);
            throw new RuntimeException("TestContainers failed to start during context initialization", e);
        }
    }
}
