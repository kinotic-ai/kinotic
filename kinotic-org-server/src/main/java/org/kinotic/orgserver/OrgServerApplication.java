package org.kinotic.orgserver;

import org.kinotic.core.api.annotations.EnableKinotic;
import org.kinotic.domain.api.rest.DeviceAuthorizationHandler;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

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
}
