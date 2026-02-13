package org.kinotic.structuresserver;

import org.kinotic.continuum.api.annotations.EnableContinuum;
import org.kinotic.continuum.gateway.api.annotations.EnableContinuumGateway;
import org.kinotic.structures.api.annotations.EnableStructures;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication()
@EnableContinuum
@EnableContinuumGateway
@EnableStructures
public class StructuresServerApplication {
	public static void main(String[] args) {
		SpringApplication.run(StructuresServerApplication.class, args);
	}
}
