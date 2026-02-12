

package org.kinotic.orchestrator;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jmx.JmxAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 *
 * Created by navid on 10/17/19
 */
@SpringBootApplication(exclude = {
        JmxAutoConfiguration.class})
@EnableConfigurationProperties
public class TestOrchestratorApplication {
}
