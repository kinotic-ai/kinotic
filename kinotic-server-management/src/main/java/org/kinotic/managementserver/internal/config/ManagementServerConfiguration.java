package org.kinotic.managementserver.internal.config;

import org.kinotic.core.api.event.ZonePartitioningService;
import org.kinotic.domain.api.utils.DomainUtil;
import org.kinotic.managementserver.api.config.KinoticManagementServerProperties;
import org.kinotic.managementserver.api.config.ManagementServerProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Set;

/**
 * What the management server is: the zones it hosts and reaches, and the properties that name its URLs.
 */
@Configuration
public class ManagementServerConfiguration {

	/**
	 * Hosts the management-api services, and reaches the system-api services deployments run through and the
	 * app-api services the portal's entity browser calls.
	 */
	@Bean
	public ZonePartitioningService zonePartitioningService() {
		return ZonePartitioningService.of("management",
								          Set.of(DomainUtil.MANAGEMENT_API_ZONE),
								          Set.of(DomainUtil.MANAGEMENT_API_ZONE, DomainUtil.SYSTEM_API_ZONE, DomainUtil.APP_API_ZONE));
	}

	/**
	 * Makes the ManagementServerProperties bean available for use by other beans without needing to
	 * inject {@link KinoticManagementServerProperties}
	 */
	@Bean
	public ManagementServerProperties managementServerProperties(KinoticManagementServerProperties properties) {
		return properties.getManagementServer();
	}
}
