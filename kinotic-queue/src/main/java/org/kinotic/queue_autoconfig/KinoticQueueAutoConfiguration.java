package org.kinotic.queue_autoconfig;

import org.kinotic.queue.KinoticQueueLibrary;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Import;

/**
 * This is the autoconfiguration class for this library
 * It is defined in a separate package because it must not be scanned by the spring context
 */
@AutoConfiguration
@Import(KinoticQueueLibrary.class)
public class KinoticQueueAutoConfiguration {
}
