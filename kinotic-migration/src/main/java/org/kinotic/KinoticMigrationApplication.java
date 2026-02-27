package org.kinotic;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication()
@EnableConfigurationProperties
public class KinoticMigrationApplication {
	public static void main(String[] args) {
		SpringApplication.run(KinoticMigrationApplication.class, args);
	}
}
