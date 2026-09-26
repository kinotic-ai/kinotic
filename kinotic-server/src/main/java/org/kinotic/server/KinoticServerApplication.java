package org.kinotic.server;

import org.kinotic.core.api.annotations.EnableKinotic;
import org.kinotic.domain.api.rest.DeviceAuthorizationHandler;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@SpringBootApplication()
@EnableKinotic
// the CLI logs in to this server through the device grant
@Import(DeviceAuthorizationHandler.class)
public class KinoticServerApplication {
	static void main(String[] args) {
		SpringApplication.run(KinoticServerApplication.class, args);
	}
}
