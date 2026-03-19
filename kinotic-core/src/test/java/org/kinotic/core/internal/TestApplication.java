

package org.kinotic.core.internal;

import org.kinotic.core.api.annotations.EnableKinotic;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jmx.JmxAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.test.context.ActiveProfiles;

/**
 *
 * Created by navid on 10/17/19
 */
@SpringBootApplication(exclude = {JmxAutoConfiguration.class})
@EnableConfigurationProperties
@EnableKinotic
@ActiveProfiles({"test"})
public class TestApplication {
}
