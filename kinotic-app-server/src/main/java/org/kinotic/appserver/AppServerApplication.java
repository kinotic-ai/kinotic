package org.kinotic.appserver;

import org.kinotic.core.api.annotations.EnableKinotic;
import org.kinotic.core.api.event.ZonePartition;
import org.kinotic.domain.api.utils.DomainUtil;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.util.Set;

/**
 * The app server: the API every application's users, UIs and microservices call, the app-api
 * persistence services and the services the applications publish in their own zones.
 */
@SpringBootApplication()
@EnableKinotic
public class AppServerApplication {
	static void main(String[] args) {
		SpringApplication.run(AppServerApplication.class, args);
	}

	/**
	 * Hosts and reaches the app-api services and the services every application publishes in its
	 * {@code app.<organizationId>.<applicationId>} zone.
	 */
	@Bean
	public ZonePartition zonePartition() {
		Set<String> zones = Set.of(DomainUtil.APP_API_ZONE, DomainUtil.APP_ZONE_PREFIX);
		return ZonePartition.of("app", zones, zones);
	}
}
