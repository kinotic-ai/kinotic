package org.kinotic.structures;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.elasticsearch.ReactiveElasticsearchClientAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication(exclude = {ReactiveElasticsearchClientAutoConfiguration.class})
@EnableConfigurationProperties
public class StructuresMigrationApplication {
	public static void main(String[] args) {
		SpringApplication.run(StructuresMigrationApplication.class, args);
	}
}
