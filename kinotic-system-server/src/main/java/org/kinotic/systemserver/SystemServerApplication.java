package org.kinotic.systemserver;

import org.kinotic.core.api.annotations.EnableKinotic;
import org.kinotic.core.api.event.ZonePartition;
import org.kinotic.domain.api.utils.DomainUtil;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.util.Set;

/**
 * The system server: the system console's API, the system-api services platform operators and the
 * vm-managers call, and the deployment of every organization's projects.
 */
@SpringBootApplication()
@EnableKinotic
public class SystemServerApplication {
	static void main(String[] args) {
		SpringApplication.run(SystemServerApplication.class, args);
	}

	/**
	 * Hosts and reaches the system-api services and the management-api services the system console calls.
	 */
	@Bean
	public ZonePartition zonePartition() {
		Set<String> zones = Set.of(DomainUtil.SYSTEM_API_ZONE, DomainUtil.MANAGEMENT_API_ZONE);
		return ZonePartition.of("system", zones, zones);
	}
}
