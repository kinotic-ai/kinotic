package org.mindignited.structuresserver;

import org.mindignited.continuum.api.annotations.EnableContinuum;
import org.mindignited.continuum.gateway.api.annotations.EnableContinuumGateway;
import org.mindignited.structures.api.annotations.EnableStructures;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.elasticsearch.ReactiveElasticsearchClientAutoConfiguration;
import org.springframework.boot.autoconfigure.hazelcast.HazelcastAutoConfiguration;

@SpringBootApplication(exclude = {HazelcastAutoConfiguration.class,
								  JpaRepositoriesAutoConfiguration.class,
								  ReactiveElasticsearchClientAutoConfiguration.class})
@EnableContinuum
@EnableContinuumGateway
@EnableStructures
public class StructuresServerApplication {
	public static void main(String[] args) {
		SpringApplication.run(StructuresServerApplication.class, args);
	}
}
