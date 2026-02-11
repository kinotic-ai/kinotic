package org.kinotic.server;

import org.kinotic.continuum.api.annotations.EnableContinuum;
import org.kinotic.continuum.gateway.api.annotations.EnableContinuumGateway;
import org.kinotic.persistence.api.annotations.EnableStructures;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication()
@EnableContinuum
@EnableContinuumGateway
@EnableStructures
public class KinoticServerApplication {
	public static void main(String[] args) {
		SpringApplication.run(KinoticServerApplication.class, args);
	}
}
