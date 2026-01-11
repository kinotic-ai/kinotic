package org.mindignited.structuresserver;

import org.mindignited.structuresserver.config.ElasticsearchTestContextInitializer;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.elasticsearch.ReactiveElasticsearchClientAutoConfiguration;
import org.springframework.boot.autoconfigure.hazelcast.HazelcastAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = ElasticsearchTestContextInitializer.class)
@EnableAutoConfiguration(exclude = {HazelcastAutoConfiguration.class,
                                    JpaRepositoriesAutoConfiguration.class,
                                    ReactiveElasticsearchClientAutoConfiguration.class})
public class StructuresServerTestApplication {
    public static void main(String[] args) {
		SpringApplication.run(StructuresServerTestApplication.class, args);
	}

}
