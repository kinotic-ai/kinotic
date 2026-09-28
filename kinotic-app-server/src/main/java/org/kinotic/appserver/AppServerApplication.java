package org.kinotic.appserver;

import org.kinotic.core.api.annotations.EnableKinotic;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * The app server: the API every application's users, UIs and microservices call, the app-api
 * persistence services and the services the applications publish in their own zones.
 */
@SpringBootApplication()
@EnableKinotic
public class AppServerApplication {
	static void main(String[] args) {
		SpringApplication.run(AppServerApplication.class, args);
	}
}
