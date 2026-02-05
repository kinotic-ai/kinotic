package org.mindignited.structuresserver;

import org.kinotic.continuum.api.annotations.EnableContinuum;
import org.kinotic.continuum.gateway.api.annotations.EnableContinuumGateway;
import org.mindignited.structures.api.annotations.EnableStructures;
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
