package org.kinotic.orgserver;

import org.kinotic.core.api.annotations.EnableKinotic;
import org.kinotic.core.api.event.ZonePartition;
import org.kinotic.domain.api.rest.DeviceAuthorizationHandler;
import org.kinotic.domain.api.utils.DomainUtil;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import java.util.Set;

/**
 * The org server: the portal's API and the management-api services organization members, their
 * machines and the Kinotic CLI call.
 */
@SpringBootApplication()
@EnableKinotic
// the CLI logs in to this server through the device grant
@Import(DeviceAuthorizationHandler.class)
public class OrgServerApplication {
	static void main(String[] args) {
		SpringApplication.run(OrgServerApplication.class, args);
	}

	/**
	 * Hosts the management-api services, and reaches the system-api services deployments run through and the
	 * app-api services the portal's entity browser calls.
	 */
	@Bean
	public ZonePartition zonePartition() {
		return ZonePartition.of("org",
								Set.of(DomainUtil.MANAGEMENT_API_ZONE),
								Set.of(DomainUtil.MANAGEMENT_API_ZONE, DomainUtil.SYSTEM_API_ZONE, DomainUtil.APP_API_ZONE));
	}
}
