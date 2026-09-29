package org.kinotic.core_autoconfig;

import org.kinotic.core.KinoticCoreLibrary;
import org.kinotic.core.api.event.ZonePartitioning;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

/**
 * This is the autoconfiguration class for this library
 * It is defined in a separate package because it must not be scanned by the spring context
 */
@AutoConfiguration
@Import(KinoticCoreLibrary.class)
public class KinoticCoreAutoConfiguration {

    // Declared here rather than scanned: auto-configuration runs after the server module's own beans are
    // registered, so @ConditionalOnMissingBean sees a server's ZonePartitioning whatever the scan order
    @Bean
    @ConditionalOnMissingBean(ZonePartitioning.class)
    public ZonePartitioning everyZonePartitioning() {
        return ZonePartitioning.everyZone("kinotic");
    }
}
