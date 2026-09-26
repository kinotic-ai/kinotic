package org.kinotic.systemserver;

import org.kinotic.core.api.annotations.EnableKinotic;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

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
}
