package org.kinotic.appserver.internal.config;

import org.kinotic.appserver.api.config.AppServerProperties;
import org.kinotic.core.api.event.ZonePartitioning;
import org.kinotic.domain.api.utils.DomainUtil;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Set;

/**
 * What the app server is: the zones it hosts and reaches, and the properties that name its URLs.
 */
@Configuration
@EnableConfigurationProperties(AppServerProperties.class)
public class AppServerConfiguration {

	/**
	 * Hosts and reaches the app-api services and the services every application publishes in its
	 * {@code app.<organizationId>.<applicationId>} zone.
	 */
	@Bean
	public ZonePartitioning zonePartitioning() {
		Set<String> zones = Set.of(DomainUtil.APP_API_ZONE, DomainUtil.APP_ZONE_PREFIX);
		return ZonePartitioning.of("app", zones, zones);
	}
}
