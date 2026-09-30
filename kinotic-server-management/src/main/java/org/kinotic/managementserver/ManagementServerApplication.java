package org.kinotic.managementserver;

import org.kinotic.core.api.annotations.EnableKinotic;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * The management server: the portal's API and the management-api services organization members, their
 * machines and the Kinotic CLI call.
 */
@SpringBootApplication()
@EnableKinotic
public class ManagementServerApplication {
	static void main(String[] args) {
		SpringApplication.run(ManagementServerApplication.class, args);
	}
}
