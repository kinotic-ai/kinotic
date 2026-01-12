package org.mindignited.structures.auth;

import org.mindignited.structures.auth.config.KeycloakTestContextInitializer;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;


@SpringBootApplication
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, classes = KeycloakTestContextInitializer.class)
@EnableConfigurationProperties
public class StructuresAuthTestApplication {
    public static void main(String[] args) {
		SpringApplication.run(StructuresAuthTestApplication.class, args);
	}

}
