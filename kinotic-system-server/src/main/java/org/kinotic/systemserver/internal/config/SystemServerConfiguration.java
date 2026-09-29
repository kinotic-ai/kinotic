package org.kinotic.systemserver.internal.config;

import org.kinotic.core.api.event.ZonePartitioningService;
import org.kinotic.domain.api.utils.DomainUtil;
import org.kinotic.systemserver.api.config.SystemServerProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Set;

/**
 * What the system server is: the zones it hosts and reaches, and the properties that name its URLs.
 */
@Configuration
@EnableConfigurationProperties(SystemServerProperties.class)
public class SystemServerConfiguration {

	/**
	 * Hosts and reaches the system-api services and the management-api services the system console calls.
	 */
	@Bean
	public ZonePartitioningService zonePartitioningService() {
		Set<String> zones = Set.of(DomainUtil.SYSTEM_API_ZONE, DomainUtil.MANAGEMENT_API_ZONE);
		return ZonePartitioningService.of("system", zones, zones);
	}
}
