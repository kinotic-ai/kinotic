package org.kinotic.test;

import org.kinotic.core.api.annotations.EnableKinotic;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.test.context.ActiveProfiles;


@SpringBootApplication
@ActiveProfiles("test")
@EnableConfigurationProperties
@EnableKinotic
public class KinoticTestApplication {
    static void main(String[] args) {
		SpringApplication.run(KinoticTestApplication.class, args);
	}

}
