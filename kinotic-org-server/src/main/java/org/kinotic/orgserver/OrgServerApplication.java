package org.kinotic.orgserver;

import org.kinotic.core.api.annotations.EnableKinotic;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * The org server: the portal's API and the management-api services organization members, their
 * machines and the Kinotic CLI call.
 */
@SpringBootApplication()
@EnableKinotic
public class OrgServerApplication {
	static void main(String[] args) {
		SpringApplication.run(OrgServerApplication.class, args);
	}
}
