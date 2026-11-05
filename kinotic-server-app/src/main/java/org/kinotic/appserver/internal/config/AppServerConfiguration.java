package org.kinotic.appserver.internal.config;

import org.kinotic.appserver.api.config.AppServerProperties;
import org.kinotic.appserver.api.config.KinoticAppServerProperties;
import org.kinotic.core.api.event.ZonePartitioningService;
import org.kinotic.domain.api.utils.DomainUtil;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Set;

/**
 * What the app server is: the zones it hosts and reaches, and the properties that name its URLs.
 */
@Configuration
public class AppServerConfiguration {

	/**
	 * Hosts and reaches the app-api services and the services every application publishes in its
	 * {@code app.<organizationId>.<applicationId>} zone.
	 */
	@Bean
	public ZonePartitioningService zonePartitioningService() {
		Set<String> zones = Set.of(DomainUtil.APP_API_ZONE, DomainUtil.APP_ZONE_PREFIX);
		return ZonePartitioningService.of("app", zones, zones);
	}

	/**
	 * Makes the AppServerProperties bean available for use by other beans without needing to
	 * inject {@link KinoticAppServerProperties}
	 */
	@Bean
	public AppServerProperties appServerProperties(KinoticAppServerProperties properties) {
		return properties.getAppServer();
	}
}
