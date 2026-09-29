package org.kinotic.orgserver.internal.config;

import org.kinotic.core.api.event.ZonePartitioning;
import org.kinotic.domain.api.utils.DomainUtil;
import org.kinotic.orgserver.api.config.OrgServerProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Set;

/**
 * What the org server is: the zones it hosts and reaches, and the properties that name its URLs.
 */
@Configuration
@EnableConfigurationProperties(OrgServerProperties.class)
public class OrgServerConfiguration {

	/**
	 * Hosts the management-api services, and reaches the system-api services deployments run through and the
	 * app-api services the portal's entity browser calls.
	 */
	@Bean
	public ZonePartitioning zonePartitioning() {
		return ZonePartitioning.of("org",
								   Set.of(DomainUtil.MANAGEMENT_API_ZONE),
								   Set.of(DomainUtil.MANAGEMENT_API_ZONE, DomainUtil.SYSTEM_API_ZONE, DomainUtil.APP_API_ZONE));
	}
}
