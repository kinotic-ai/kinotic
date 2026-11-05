package org.kinotic.queue_test;

import org.kinotic.core.api.annotations.EnableKinotic;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jmx.JmxAutoConfiguration;

// Outside org.kinotic.queue, so the queue module's component scan in QueueTestNode does not pick it up
@SpringBootApplication(exclude = {JmxAutoConfiguration.class})
@EnableKinotic
public class QueueTestApplication {
}
