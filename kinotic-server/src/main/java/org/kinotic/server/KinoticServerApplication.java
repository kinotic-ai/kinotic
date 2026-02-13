package org.kinotic.server;

import org.kinotic.boot.api.annotations.EnableKinotic;
import org.kinotic.rpc.gateway.api.annotations.EnableContinuumGateway;
import org.kinotic.persistence.api.annotations.EnableStructures;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication()
@EnableKinotic
@EnableContinuumGateway
@EnableStructures
public class KinoticServerApplication {
	public static void main(String[] args) {
		SpringApplication.run(KinoticServerApplication.class, args);
	}
}
