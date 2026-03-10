package org.kinotic.server;

import org.kinotic.core.api.annotations.EnableKinotic;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication()
@EnableKinotic
public class KinoticServerApplication {
	static void main(String[] args) {
		SpringApplication.run(KinoticServerApplication.class, args);
	}
}
