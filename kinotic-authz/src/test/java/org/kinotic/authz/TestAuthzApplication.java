package org.kinotic.authz;

import org.kinotic.core.api.annotations.EnableKinotic;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jmx.JmxAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.test.context.ActiveProfiles;

/**
 * The application the authorization module's tests run in: the module on top of a core node.
 */
@SpringBootApplication(exclude = {JmxAutoConfiguration.class})
@EnableConfigurationProperties
@EnableKinotic
@ActiveProfiles({"test"})
public class TestAuthzApplication {
}
