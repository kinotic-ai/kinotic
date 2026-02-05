package org.kinotic.structures;

import org.kinotic.structures.support.keycloak.KeycloakTestContextInitializer;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.elasticsearch.ReactiveElasticsearchClientAutoConfiguration;
import org.springframework.boot.autoconfigure.hazelcast.HazelcastAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;


@SpringBootApplication(exclude = {HazelcastAutoConfiguration.class,
    JpaRepositoriesAutoConfiguration.class,
    ReactiveElasticsearchClientAutoConfiguration.class})
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, classes = KeycloakTestContextInitializer.class)
@EnableConfigurationProperties
public class StructuresTestApplication {
    public static void main(String[] args) {
		SpringApplication.run(StructuresTestApplication.class, args);
	}

}
