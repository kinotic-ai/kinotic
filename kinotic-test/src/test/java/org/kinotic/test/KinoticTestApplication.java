package org.kinotic.test;

import org.kinotic.core.api.annotations.EnableKinotic;
import org.kinotic.test.support.keycloak.KeycloakTestContextInitializer;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;


@SpringBootApplication
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, classes = KeycloakTestContextInitializer.class)
@EnableConfigurationProperties
@EnableKinotic
public class KinoticTestApplication {
    static void main(String[] args) {
		SpringApplication.run(KinoticTestApplication.class, args);
	}

}
