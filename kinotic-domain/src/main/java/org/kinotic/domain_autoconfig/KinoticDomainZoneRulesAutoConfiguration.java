package org.kinotic.domain_autoconfig;

import org.kinotic.core.api.event.ZoneRulesResolver;
import org.kinotic.domain.internal.api.services.security.ScopedParticipantZoneRulesResolver;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * Supplies the zone rules of Kinotic OS's participants to every server that authenticates them, including one that
 * excludes {@link KinoticDomainAutoConfiguration} because it uses only kinotic-domain's participant types.
 */
@AutoConfiguration
public class KinoticDomainZoneRulesAutoConfiguration {

    @Bean
    public ZoneRulesResolver zoneRulesResolver() {
        return new ScopedParticipantZoneRulesResolver();
    }
}
